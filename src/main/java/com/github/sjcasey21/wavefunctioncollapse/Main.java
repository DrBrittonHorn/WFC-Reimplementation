package com.github.sjcasey21.wavefunctioncollapse;

import java.io.*;
import java.util.*;

public class Main {

    static char[][] loadInputTextFile(String path) throws IOException {
        List<char[]> rows = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new FileReader(path));
        String line;
        while ((line = reader.readLine()) != null) {
            rows.add(line.trim().toCharArray());
        }
        reader.close();
        return rows.toArray(new char[rows.size()][]);
    }

    static void runTextWFC() {
        try {
            char[][] input = loadInputTextFile("input.txt");
            int height = input.length;
            int width = input[0].length;
            Random random = new Random();

            TextWFCModel model = new TextWFCModel(input, width, height);
            boolean finished = model.run();
            System.out.println("Finished: " + finished);

            BufferedWriter writer = new BufferedWriter(new FileWriter("txt_out.txt"));
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    writer.write(model.getOutput()[y][x]);
                }
                writer.newLine();
            }
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        runTextWFC();
    }
}
