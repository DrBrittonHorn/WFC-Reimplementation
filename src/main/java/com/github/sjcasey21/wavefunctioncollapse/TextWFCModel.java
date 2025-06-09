package com.github.sjcasey21.wavefunctioncollapse;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class TextWFCModel {

    private final int outputWidth, outputHeight, chunkWidth, chunkHeight;
    private final int[][] input;
    private final char[][] finalOutput;
    private final List<int[][]> tiles = new ArrayList<>();
    private final Map<String, Integer> tileIds = new HashMap<>();
    private final Map<Integer, String> idToSymbol = new HashMap<>();
    private final Map<String, Integer> symbolToId = new HashMap<>();
    private final Map<Integer, Integer> tileFrequencies = new HashMap<>();

    private final boolean[][][] wave;
    private final boolean[][] observed;
    private final Stack<Point> stack = new Stack<>();
    private final Random random = new Random();
    private final Map<Integer, Set<Integer>>[] adjacencyRules = new HashMap[4];

    private int gridWidth, gridHeight;

    public TextWFCModel(char[][] inputChars, int outputWidth, int outputHeight, int chunkWidth, int chunkHeight) {
        this.chunkWidth = chunkWidth;
        this.chunkHeight = chunkHeight;

        this.outputWidth = outputWidth;
        this.outputHeight = outputHeight;

        this.gridWidth = (int) Math.ceil((double) outputWidth / chunkWidth);
        this.gridHeight = (int) Math.ceil((double) outputHeight / chunkHeight);

        this.finalOutput = new char[outputHeight][outputWidth];

        // Convert input chars to int IDs
        this.input = new int[inputChars.length][inputChars[0].length];
        int id = 0;
        for (int y = 0; y < inputChars.length; y++) {
            for (int x = 0; x < inputChars[0].length; x++) {
                char ch = inputChars[y][x];
                if (!symbolToId.containsKey(String.valueOf(ch))) {
                    symbolToId.put(String.valueOf(ch), id);
                    idToSymbol.put(id, String.valueOf(ch));
                    id++;
                }
                input[y][x] = symbolToId.get(String.valueOf(ch));
            }
        }

        extractTiles();
        inferAdjacency();

        int tileCount = tiles.size();
        wave = new boolean[gridHeight][gridWidth][tileCount];
        observed = new boolean[gridHeight][gridWidth];

        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                Arrays.fill(wave[y][x], true);
            }
        }
    }

    private void extractTiles() {
        AtomicInteger idCounter = new AtomicInteger(0);
        for (int y = 0; y < input.length; y++) {
            for (int x = 0; x < input[0].length; x++) {
                int maxChunkHeight = Math.min(chunkHeight, input.length - y);
                int maxChunkWidth = Math.min(chunkWidth, input[0].length - x);

                if (maxChunkHeight <= 0 || maxChunkWidth <= 0) continue;

                int[][] chunk = new int[maxChunkHeight][maxChunkWidth];
                for (int dy = 0; dy < maxChunkHeight; dy++) {
                    for (int dx = 0; dx < maxChunkWidth; dx++) {
                        chunk[dy][dx] = input[y + dy][x + dx];
                    }
                }
                String key = Arrays.deepToString(chunk);
                int tileId = tileIds.computeIfAbsent(key, k -> {
                    tiles.add(chunk);
                    return idCounter.getAndIncrement();
                });
                tileFrequencies.put(tileId, tileFrequencies.getOrDefault(tileId, 0) + 1);
            }
        }
    }

    private void inferAdjacency() {
        for (int i = 0; i < 4; i++) adjacencyRules[i] = new HashMap<>();
        for (int i = 0; i < tiles.size(); i++) {
            for (int j = 0; j < tiles.size(); j++) {
                for (int dir = 0; dir < 4; dir++) {
                    if (canBeAdjacent(tiles.get(i), tiles.get(j), dir)) {
                        adjacencyRules[dir].computeIfAbsent(i, k -> new HashSet<>()).add(j);
                    }
                }
            }
        }
    }

    private boolean canBeAdjacent(int[][] a, int[][] b, int dir) {
        int ha = a.length, wa = a[0].length;
        int hb = b.length, wb = b[0].length;

        switch (dir) {
            case 0: // up
                if (wa != wb) return false;
                for (int i = 0; i < wa; i++) if (a[0][i] != b[hb - 1][i]) return false;
                break;
            case 1: // right
                if (ha != hb) return false;
                for (int i = 0; i < ha; i++) if (a[i][wa - 1] != b[i][0]) return false;
                break;
            case 2: // down
                if (wa != wb) return false;
                for (int i = 0; i < wa; i++) if (a[ha - 1][i] != b[0][i]) return false;
                break;
            case 3: // left
                if (ha != hb) return false;
                for (int i = 0; i < ha; i++) if (a[i][0] != b[i][wb - 1]) return false;
                break;
        }
        return true;
    }

    public boolean run() {
        while (true) {
            int[] coords = observe();
            if (coords == null) break;
            if (coords.length == 0) return false;

            int y = coords[0], x = coords[1];
            List<Integer> options = getPossibleTiles(y, x);
            if (options.isEmpty()) return false;

            int total = 0;
            for (int opt : options) total += tileFrequencies.getOrDefault(opt, 1);
            int r = random.nextInt(total);
            int chosen = options.get(0);
            for (int opt : options) {
                r -= tileFrequencies.getOrDefault(opt, 1);
                if (r < 0) {
                    chosen = opt;
                    break;
                }
            }

            for (int t = 0; t < tiles.size(); t++) {
                if (t != chosen) ban(x, y, t);
            }

            observed[y][x] = true;
            propagate();
        }

        reconstructOutput();
        return true;
    }

    private int[] observe() {
        double minEntropy = Double.POSITIVE_INFINITY;
        int[] result = null;
        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                if (observed[y][x]) continue;
                List<Integer> options = getPossibleTiles(y, x);
                if (options.isEmpty()) return new int[0];
                if (options.size() == 1) continue;
                double entropy = Math.log(options.size()) + random.nextDouble() * 1e-6;
                if (entropy < minEntropy) {
                    minEntropy = entropy;
                    result = new int[]{y, x};
                }
            }
        }
        return result;
    }

    private void propagate() {
        while (!stack.isEmpty()) {
            Point p = stack.pop();
            int x = p.x, y = p.y, t = p.z;
            for (int dir = 0; dir < 4; dir++) {
                int dx = (dir == 1) ? 1 : (dir == 3) ? -1 : 0;
                int dy = (dir == 2) ? 1 : (dir == 0) ? -1 : 0;
                int nx = x + dx, ny = y + dy;
                if (nx < 0 || ny < 0 || nx >= gridWidth || ny >= gridHeight) continue;

                for (int t2 = 0; t2 < tiles.size(); t2++) {
                    if (!wave[ny][nx][t2]) continue;
                    boolean valid = false;
                    for (int t3 = 0; t3 < tiles.size(); t3++) {
                        if (wave[y][x][t3] &&
                            adjacencyRules[dir].getOrDefault(t3, Collections.emptySet()).contains(t2)) {
                            valid = true;
                            break;
                        }
                    }
                    if (!valid) ban(nx, ny, t2);
                }
            }
        }
    }

    private void ban(int x, int y, int t) {
        if (!wave[y][x][t]) return;
        wave[y][x][t] = false;
        stack.push(new Point(x, y, t));
    }

    private List<Integer> getPossibleTiles(int y, int x) {
        List<Integer> result = new ArrayList<>();
        for (int t = 0; t < tiles.size(); t++) {
            if (wave[y][x][t]) result.add(t);
        }
        return result;
    }

    private void reconstructOutput() {
        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                for (int t = 0; t < tiles.size(); t++) {
                    if (wave[y][x][t]) {
                        int[][] tile = tiles.get(t);
                        for (int dy = 0; dy < tile.length; dy++) {
                            for (int dx = 0; dx < tile[0].length; dx++) {
                                int fy = y * chunkHeight + dy;
                                int fx = x * chunkWidth + dx;
                                if (fy < outputHeight && fx < outputWidth) {
                                    finalOutput[fy][fx] = idToSymbol.get(tile[dy][dx]).charAt(0);
                                }
                            }
                        }
                        break;
                    }
                }
            }
        }
    }

    public char[][] getFinalOutput() {
        return finalOutput;
    }

    private static class Point {
        int x, y, z;
        Point(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
    }
}
