package com.ast.unreal;
import java.io.*;
import java.nio.file.*;
public class GoldDataVersionTest {
    private static void write(File f, int version, int licensee) throws IOException {
        Files.write(f.toPath(), new byte[]{(byte)0xc1,(byte)0x83,0x2a,(byte)0x9e,
                (byte)version,(byte)(version>>8),(byte)licensee,(byte)(licensee>>8)});
    }
    private static void check(boolean condition) { if(!condition) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        Path tmp=Files.createTempDirectory("unreal-version-");
        File core=tmp.resolve("Core.u").toFile(), engine=tmp.resolve("Engine.u").toFile();
        try {
            write(core,68,0); write(engine,68,0); check(GoldDataVersion.supports(core,engine));
            write(core,61,0); check(!GoldDataVersion.supports(core,engine));
            write(core,69,227); check(!GoldDataVersion.supports(core,engine));
            write(core,68,227); check(!GoldDataVersion.supports(core,engine));
            write(core,69,0); check(!GoldDataVersion.supports(core,engine));
            write(core,68,0); write(engine,69,227); check(!GoldDataVersion.supports(core,engine));
            write(engine,68,0); Files.write(core.toPath(),new byte[]{0,0}); check(!GoldDataVersion.supports(core,engine));
            write(core,68,0); byte[] bytes=Files.readAllBytes(core.toPath()); bytes[0]=0;
            Files.write(core.toPath(),bytes); check(!GoldDataVersion.supports(core,engine));
            core.delete(); check(!GoldDataVersion.supports(core,engine));
            System.out.println("Gold data version: retail, 227, mixed, truncated, invalid and missing packages passed");
        } finally { core.delete(); engine.delete(); Files.delete(tmp); }
    }
}
