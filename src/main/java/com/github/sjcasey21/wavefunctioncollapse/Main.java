package com.github.sjcasey21.wavefunctioncollapse;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get("input.txt"));
        int inputHeight = lines.size();
        int inputWidth = lines.get(0).length();
        char[][] input = new char[inputHeight][inputWidth];

        for (int y = 0; y < inputHeight; y++) {
            input[y] = lines.get(y).toCharArray();
        }

        // Choose tile size here (e.g. 2x2, 1x1, 1x5, etc.)
        int chunkWidth = 2;
        int chunkHeight = 2;

        // Output size = input size
        int outputWidth = inputWidth;
        int outputHeight = inputHeight;
		System.out.println("Input dimensions: " + inputWidth + "x" + inputHeight);

        TextWFCModel model = new TextWFCModel(input, outputWidth, outputHeight, chunkWidth, chunkHeight);
        boolean success = model.run();
        System.out.println("Success: " + success);

        char[][] output = model.getFinalOutput();
        for (char[] row : output) {
            System.out.println(new String(row));
        }

        BufferedWriter writer = new BufferedWriter(new FileWriter("txt_output.txt"));
        for (char[] row : output) {
            writer.write(row);
            writer.newLine();
        }
        writer.close();
    }
}
