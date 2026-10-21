package com.ast.unreal;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/** Retail 226 has package version 68, licensee 0; patched 227 packages need another engine. */
final class GoldDataVersion {
    static boolean supports(File core, File engine) { return retail226(core) && retail226(engine); }

    private static boolean retail226(File file) {
        if (file == null) return false;
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            byte[] h = new byte[8];
            in.readFully(h);
            return (h[0] & 255) == 0xc1 && (h[1] & 255) == 0x83
                    && (h[2] & 255) == 0x2a && (h[3] & 255) == 0x9e
                    && (h[4] & 255) == 68 && h[5] == 0 && h[6] == 0 && h[7] == 0;
        } catch (IOException error) { return false; }
    }
}
