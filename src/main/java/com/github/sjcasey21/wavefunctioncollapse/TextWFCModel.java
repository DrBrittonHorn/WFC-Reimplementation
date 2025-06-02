package com.github.sjcasey21.wavefunctioncollapse;

import java.util.*;

public class TextWFCModel {

    private final int width, height;
    private final char[][] input;
    private final char[][] output;
    private final char[] symbols = { 'A', 'B', 'C' };
    private final boolean[][][] wave;
    private final boolean[][] observed; // NEW: Track observed locations
    private final Stack<Point> stack = new Stack<>();
    private final Random random = new Random();

    public TextWFCModel(char[][] input, int width, int height) {
        this.width = width;
        this.height = height;
        this.input = input;
        this.output = new char[height][width];

        // Initialize wave function: wave[y][x][t] where t is symbol index
        wave = new boolean[height][width][symbols.length];
        observed = new boolean[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Arrays.fill(wave[y][x], true); // all possibilities allowed at start
                observed[y][x] = false;
            }
        }
    }

    public boolean run() {
        while (true) {
            int[] coords = observe();
            if (coords == null) break; // all observed
            if (coords.length == 0) return false; // contradiction THERE IS AN ISSUE HERE

            int y = coords[0], x = coords[1];
            List<Integer> options = getPossibleSymbols(y, x);
            if (options.isEmpty()) return false;

            int chosen = options.get(random.nextInt(options.size()));
            for (int t = 0; t < symbols.length; t++) {
                if (t != chosen) ban(x, y, t);
            }

            observed[y][x] = true; // Mark as observed
            propagate();
        }

        // Fill output grid
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (int t = 0; t < symbols.length; t++) {
                    if (wave[y][x][t]) {
                        output[y][x] = symbols[t];
                        break;
                    }
                }
            }
        }
        return true;
    }

    private int[] observe() {
        double minEntropy = 1E+3;
        int[] argmin = null;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (observed[y][x]) continue; // NEW: skip already observed cells

                int count = 0;
                for (int t = 0; t < symbols.length; t++) {
                    if (wave[y][x][t]) count++;
                    //System.out.println("t: " + t + ", wave[0][1][t]: " + wave[0][1][t]);
                }
                if (count == 0) {
                    return new int[0]; // contradiction
                }
                if (count == 1) continue; // already effectively observed (only one option)

                double entropy = Math.log(count) + random.nextDouble() * 1E-6;
                if (entropy < minEntropy) {
                    minEntropy = entropy;
                    argmin = new int[]{y, x};
                }
            }
        }

        return argmin;
    }

    private void propagate() {
        while (!stack.isEmpty()) {
            Point p = stack.pop();
            int x = p.x, y = p.y, t = p.z;

            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (Math.abs(dx) + Math.abs(dy) != 1) continue;

                    int nx = x + dx;
                    int ny = y + dy;
                    if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;

                    for (int t2 = 0; t2 < symbols.length; t2++) {
                        if (!wave[ny][nx][t2]) continue;
                        if (!respectsConstraints(nx, ny, symbols[t2])) {
                            ban(nx, ny, t2);
                        }
                    }
                }
            }
        }
    }

    private void ban(int x, int y, int t) {
        if (!wave[y][x][t]) return;
        wave[y][x][t] = false;
        stack.add(new Point(x, y, t));

        // DEBUG: Output what's being banned
        System.out.println("BANNING symbol " + symbols[t] + " at (" + y + ", " + x + ")");
    }

    private List<Integer> getPossibleSymbols(int y, int x) {
        List<Integer> options = new ArrayList<>();
        for (int t = 0; t < symbols.length; t++) {
            if (wave[y][x][t]) {
                options.add(t);
            }
        }
        return options;
    }

    private boolean respectsConstraints(int x, int y, char candidate) {
        // Check ABOVE (y - 1)
        if (y > 0) {
            for (int t = 0; t < symbols.length; t++) {
                if (!wave[y - 1][x][t]) continue;
                char above = symbols[t];

                // A cannot have B above
                if (candidate == 'A' && above == 'B') return false;

                // C cannot have B above
                if (candidate == 'C' && above == 'B') return false;
            }
        }

        // Check BELOW (y + 1)
        if (y < height - 1) {
            for (int t = 0; t < symbols.length; t++) {
                if (!wave[y + 1][x][t]) continue;
                char below = symbols[t];

                // B cannot have A below
                //if (candidate == 'B' && below == 'A') return false;

                // C cannot have A below
                if (candidate == 'C' && below == 'A') return false;

                // A cannot have C below
                if (candidate == 'A' && below == 'C') return false;
            }
        }

        // Check LEFT (x - 1)
        if (x > 0) {
            for (int t = 0; t < symbols.length; t++) {
                if (!wave[y][x - 1][t]) continue;
                char left = symbols[t];

                // C cannot have B or C to the left
                if (candidate == 'C' && (left == 'B' || left == 'C')) return false;

                // A/B mutual exclusion left
                //if ((candidate == 'A' && left == 'B') || (candidate == 'B' && left == 'A')) return false;
            }
        }

        // Check RIGHT (x + 1)
        if (x < width - 1) {
            for (int t = 0; t < symbols.length; t++) {
                if (!wave[y][x + 1][t]) continue;
                char right = symbols[t];

                // C cannot have B or C to the right
                if (candidate == 'C' && (right == 'B' || right == 'C')) return false;

                // A/B mutual exclusion right
                //if ((candidate == 'A' && right == 'B') || (candidate == 'B' && right == 'A')) return false;
            }
        }

        return true;
    }

    public char[][] getOutput() {
        return output;
    }

    private static class Point {
        int x, y, z;
        Point(int x, int y, int z) {
            this.x = x; this.y = y; this.z = z;
        }
    }
}
