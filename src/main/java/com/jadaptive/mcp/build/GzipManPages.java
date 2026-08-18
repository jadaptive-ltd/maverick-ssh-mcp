package com.jadaptive.mcp.build;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

/**
 * Compresses generated man pages to .gz files for Linux package layouts.
 */
public final class GzipManPages {

    private GzipManPages() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: GzipManPages <man1-directory>");
        }

        Path manDirectory = Path.of(args[0]);
        if (!Files.isDirectory(manDirectory)) {
            throw new IllegalArgumentException("Not a directory: " + manDirectory);
        }

        try (Stream<Path> paths = Files.list(manDirectory)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".1"))
                    .forEach(GzipManPages::gzipAndDelete);
        }
    }

    private static void gzipAndDelete(Path source) {
        Path target = source.resolveSibling(source.getFileName() + ".gz");
        try (InputStream in = Files.newInputStream(source);
                OutputStream out = new GZIPOutputStream(Files.newOutputStream(target))) {
            in.transferTo(out);
        }
        catch (IOException e) {
            throw new IllegalStateException("Failed to gzip man page " + source, e);
        }

        try {
            Files.delete(source);
        }
        catch (IOException e) {
            throw new IllegalStateException("Failed to delete uncompressed man page " + source, e);
        }
    }
}
