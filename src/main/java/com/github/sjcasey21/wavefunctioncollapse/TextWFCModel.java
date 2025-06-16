package com.github.sjcasey21.wavefunctioncollapse;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class TextWFCModel {

    private final int outputWidth, outputHeight, chunkWidth, chunkHeight;
    private final char[][] input;
    private int[][] inputAsTiles;
    private final char[][] finalOutput;
    private final List<char[][]> tiles = new ArrayList<>();
    private final Map<String, Integer> tileIds = new HashMap<>();
    private final Map<Integer, Integer> tileFrequencies = new HashMap<>();

    private final boolean[][][] wave;
    private final boolean[][] observed;
    private final Stack<Point> stack = new Stack<>();
    private final Random random = new Random();
    private final Map<Integer, Set<Integer>>[] adjacencyRules = new HashMap[4];

    private int gridWidth, gridHeight;
    private final Set<Integer>[] borderTileKinds = new HashSet[4];

    public TextWFCModel(char[][] inputChars, int outputWidth, int outputHeight, int chunkWidth, int chunkHeight) {
        this.chunkWidth = chunkWidth;
        this.chunkHeight = chunkHeight;
        this.input = inputChars;
        this.outputWidth = outputWidth;
        this.outputHeight = outputHeight;

        this.gridWidth = (int) Math.ceil((double) outputWidth / chunkWidth);
        this.gridHeight = (int) Math.ceil((double) outputHeight / chunkHeight);
        System.out.println("Grid dimensions: " + gridWidth + "x" + gridHeight);

        this.finalOutput = new char[outputHeight][outputWidth];
        for (int y = 0; y < outputHeight; y++) Arrays.fill(finalOutput[y], '.');

        extractTiles();
        inferAdjacency();

        int tileCount = tiles.size();
        wave = new boolean[gridHeight][gridWidth][tileCount];
        observed = new boolean[gridHeight][gridWidth];
        for (int y = 0; y < gridHeight; y++)
            for (int x = 0; x < gridWidth; x++)
                Arrays.fill(wave[y][x], true);

        for (int i = 0; i < 4; i++) borderTileKinds[i] = new HashSet<>();
        for (int t = 0; t < tiles.size(); t++) {
            char[][] tile = tiles.get(t);
            for (int x = 0; x < tile[0].length; x++) {
                if (tile[0][x] == 'B') borderTileKinds[0].add(t);
                if (tile[tile.length - 1][x] == 'B') borderTileKinds[2].add(t);
            }
            for (int y = 0; y < tile.length; y++) {
                if (tile[y][0] == 'B') borderTileKinds[3].add(t);
                if (tile[y][tile[0].length - 1] == 'B') borderTileKinds[1].add(t);
            }
        }

        for (int d = 0; d < 4; d++)
            System.out.println("Direction " + d + " border tiles: " + borderTileKinds[d]);

        // REMOVE overly aggressive bans — do NOT ban at this point

        System.out.println("Valid tile count per cell (pre-propagation):");
        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                int count = 0;
                for (int t = 0; t < tiles.size(); t++) if (wave[y][x][t]) count++;
                System.out.print(count + " ");
            }
            System.out.println();
        }

        propagate();
        printCompleteWave();
    }

    private void extractTiles() {
        int inputTileWidth = (int) Math.ceil((double) input[0].length / chunkWidth);
        int inputTileHeight = (int) Math.ceil((double) input.length / chunkHeight);
        inputAsTiles = new int[inputTileHeight][inputTileWidth];
        AtomicInteger idCounter = new AtomicInteger(0);
        for (int y = 0, tiley=0; y < input.length; y+=chunkHeight, tiley++) {
            for (int x = 0,tilex=0; x < input[0].length; x+=chunkWidth, tilex++) {
                int maxChunkHeight = Math.min(chunkHeight, input.length - y);
                int maxChunkWidth = Math.min(chunkWidth, input[0].length - x);

                if (maxChunkHeight <= 0 || maxChunkWidth <= 0) continue;

                char[][] chunk = new char[maxChunkHeight][maxChunkWidth];
                for (int dy = 0; dy < maxChunkHeight; dy++) {
                    for (int dx = 0; dx < maxChunkWidth; dx++) {
                        chunk[dy][dx] = input[y + dy][x + dx];
                    }
                }
                String key = Arrays.deepToString(chunk);
                int tileId = tileIds.computeIfAbsent(key, k -> {
                    tiles.add(chunk);
                    System.out.println("Adding tileID: " + idCounter.get());
                    for (char[] row : chunk) {
                        for (char value : row) {
                           System.out.print(value);
                        }
                        System.out.println();
                    }
                    return idCounter.getAndIncrement();
                });
                tileFrequencies.put(tileId, tileFrequencies.getOrDefault(tileId, 0) + 1);
                inputAsTiles[tiley][tilex] = tileId;
            }
        }
    }

    private void inferAdjacency() {
        System.out.println("Calculating adjacency rules...");
        for (int y=0; y<inputAsTiles.length; y++) {
            for (int x=0; x<inputAsTiles[0].length; x++) {
                System.out.print("["+inputAsTiles[y][x]+"]");
            }
            System.out.println();
        }
        System.out.println("Completed adjacency rules calculation.");
        for (int i = 0; i < 4; i++) adjacencyRules[i] = new HashMap<>();
        for (int y = 0; y < inputAsTiles.length; y++) {
            for (int x = 0; x < inputAsTiles[0].length; x++) {
                int tileId = inputAsTiles[y][x];
                if (y > 0) { // Up
                    int upTileId = inputAsTiles[y - 1][x];
                    adjacencyRules[0].computeIfAbsent(tileId, k -> new HashSet<>()).add(upTileId);
                }
                if (x < inputAsTiles[0].length - 1) { // Right
                    int rightTileId = inputAsTiles[y][x + 1];
                    adjacencyRules[1].computeIfAbsent(tileId, k -> new HashSet<>()).add(rightTileId);
                }
                if (y < inputAsTiles.length - 1) { // Down
                    int downTileId = inputAsTiles[y + 1][x];
                    adjacencyRules[2].computeIfAbsent(tileId, k -> new HashSet<>()).add(downTileId);
                }
                if (x > 0) { // Left
                    int leftTileId = inputAsTiles[y][x - 1];
                    adjacencyRules[3].computeIfAbsent(tileId, k -> new HashSet<>()).add(leftTileId);
                }
            }
        }
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
            System.out.println("Chosen tile at (" + x + ", " + y + "): " + chosen);
            for (int t = 0; t < tiles.size(); t++) {
                if (t != chosen) ban(x, y, t);
            }
            printCompleteWave();
            observed[y][x] = true;
            propagate();
            printCompleteWave();
        }

        printCurrentWave();
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
            //printCompleteWave();
            //System.out.println("Propagating from point: " + p.x + ", " + p.y + ", " + p.z);
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
                        if (!wave[y][x][t3]) continue;

                        // Allow t2 if t3 has no known adjacents in this direction
                        Set<Integer> adj = adjacencyRules[dir].get(t3);
                        if (adj == null || adj.isEmpty() || adj.contains(t2)) {
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
                int chosen = -1;
                for (int t = 0; t < tiles.size(); t++) {
                    if (wave[y][x][t]) {
                        chosen = t;
                        break;
                    }
                }
                if (chosen == -1) continue; // contradiction or uncollapsed

                char[][] tile = tiles.get(chosen);
                for (int dy = 0; dy < tile.length; dy++) {
                    for (int dx = 0; dx < tile[0].length; dx++) {
                        int fy = y * chunkHeight + dy;
                        int fx = x * chunkWidth + dx;
                        if (fy < outputHeight && fx < outputWidth) {
                            finalOutput[fy][fx] = tile[dy][dx];
                        }
                    }
                }
            }
        }

        printFinalOutput();
    }


    private void printFinalOutput() {
        System.out.println("Final Output:");
        for (char[] row : finalOutput) {
            System.out.println(new String(row));
        }
    }

    public char[][] getFinalOutput() {
        return finalOutput;
    }

    private static class Point {
        int x, y, z;
        Point(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
    }

    public void printCurrentWave() {
        System.out.println("Current Wave:");
        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                int obsTile = -1;
                boolean collapsed = true;
                System.out.print("[");
                for (int t = 0; t < tiles.size(); t++) {
                    if (wave[y][x][t]) {
                        if (obsTile != -1) {
                            collapsed = false;
                        }
                        obsTile = t;
                    }

                }
                if (collapsed) {
                    System.out.print(obsTile);
                } else {
                    System.out.print(" ");
                }
                System.out.print("]");
            }
            System.out.println();
        }
    }

    public void printCompleteWave() {
        System.out.println("Complete Wave:");
        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                int obsTile = -1;
                System.out.print("[");
                for (int t = 0; t < tiles.size(); t++) {
                    if (wave[y][x][t]) {
                        if (obsTile != -1) {
                            System.out.print(","+t);
                        } else {
                            System.out.print(t);
                        }
                        obsTile = t;
                    }

                }
                System.out.print("]");
            }
            System.out.println();
        }
    }
}
