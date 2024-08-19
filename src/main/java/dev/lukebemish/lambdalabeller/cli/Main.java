package dev.lukebemish.lambdalabeller.cli;

import org.jspecify.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@CommandLine.Command(name = "lambda-labeller", description = "Labels or un-labels lambda methods in bytecode with their name")
public class Main {
    @CommandLine.Parameters(index = "0", description = "Input jar")
    Path input;

    @CommandLine.Parameters(index = "1", description = "Output jar")
    Path output;

    @CommandLine.Option(names = "--batch-size", description = "How many class files to process at once")
    int batchSize = Runtime.getRuntime().availableProcessors();

    @CommandLine.Command(name = "label", description = "Labels lambdas in the provided jar")
    void label(
            @CommandLine.Option(names = "--stubs", description = "Path to an archive where stubs for the generated lambda label methods should go")
            Path stubsOut
    ) {
        try {
            output = output.toAbsolutePath();
            input = input.toAbsolutePath();
            Files.createDirectories(output.getParent());
            Set<String> names = stubsOut != null ? ConcurrentHashMap.newKeySet() : null;
            try (var is = Files.newInputStream(input);
                 var os = Files.newOutputStream(output);
                 var zis = new ZipInputStream(is);
                 var zos = new ZipOutputStream(os)) {
                Entry[] entries = new Entry[batchSize];
                byte[][] labelled = new byte[batchSize][];
                Future<?>[] futures = new Future[batchSize];
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    int i = 0;
                    while (i < batchSize && entry != null) {
                        var bytes = new ByteArrayOutputStream();
                        zis.transferTo(bytes);
                        entries[i] = new Entry(entry, bytes.toByteArray());
                        i++;
                        if (i < batchSize) {
                            entry = zis.getNextEntry();
                        }
                    }
                    for (; i < batchSize; i++) {
                        entries[i] = null;
                        labelled[i] = new byte[0];
                    }

                    labelEntries(names, labelled, entries, futures);

                    for (int j = 0; j < batchSize; j++) {
                        var entryIn = entries[j];
                        if (entryIn == null) {
                            continue;
                        }
                        ZipEntry zipEntry = entryIn.entry();
                        byte[] bytes = labelled[j];
                        zos.putNextEntry(zipEntry);
                        zos.write(bytes);
                        zos.closeEntry();
                    }
                }
            }
            if (stubsOut != null) {
                Files.createDirectories(stubsOut.toAbsolutePath().getParent());
                try (var os = Files.newOutputStream(stubsOut.toAbsolutePath());
                     var zos = new ZipOutputStream(os)) {
                    zos.putNextEntry(new ZipEntry(Shared.LABEL_CLASS+".class"));
                    var writer = new ClassWriter(0);
                    new StubWriter(names).create(writer);
                    byte[] bytes = writer.toByteArray();
                    zos.write(bytes);
                    zos.closeEntry();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @CommandLine.Command(name = "repair", description = "Repair lambda names using labels")
    void repair() {
        try {
            output = output.toAbsolutePath();
            input = input.toAbsolutePath();
            Files.createDirectories(output.getParent());
            try (var is = Files.newInputStream(input);
                 var os = Files.newOutputStream(output);
                 var zis = new ZipInputStream(is);
                 var zos = new ZipOutputStream(os)) {
                Entry[] entries = new Entry[batchSize];
                byte[][] labelled = new byte[batchSize][];
                Future<?>[] futures = new Future[batchSize];
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    int i = 0;
                    while (i < batchSize && entry != null) {
                        var bytes = new ByteArrayOutputStream();
                        zis.transferTo(bytes);
                        entries[i] = new Entry(entry, bytes.toByteArray());
                        i++;
                        if (i < batchSize) {
                            entry = zis.getNextEntry();
                        }
                    }
                    for (; i < batchSize; i++) {
                        entries[i] = null;
                        labelled[i] = new byte[0];
                    }

                    repairEntries(labelled, entries, futures);

                    for (int j = 0; j < batchSize; j++) {
                        var entryIn = entries[j];
                        if (entryIn == null) {
                            continue;
                        }
                        ZipEntry zipEntry = entryIn.entry();
                        byte[] bytes = labelled[j];
                        zos.putNextEntry(zipEntry);
                        zos.write(bytes);
                        zos.closeEntry();
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private final ExecutorService executorService = Executors.newFixedThreadPool(batchSize);

    private void labelEntries(@Nullable Set<String> names, byte[][] labelled, Entry[] entries, Future<?>[] futures) {
        for (int i = 0; i < batchSize; i++) {
            var entry = entries[i];
            if (entry == null) {
                continue;
            }
            var number = i;
            futures[i] = executorService.submit(() -> {
                if (entry.entry().getName().endsWith(".class")) {
                    labelled[number] = labelClass(names, entry.contents());
                } else {
                    labelled[number] = entry.contents();
                }
            });
        }
        for (int i = 0; i < batchSize; i++) {
            try {
                futures[i].get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void repairEntries(byte[][] labelled, Entry[] entries, Future<?>[] futures) {
        for (int i = 0; i < batchSize; i++) {
            var entry = entries[i];
            if (entry == null) {
                continue;
            }
            var number = i;
            futures[i] = executorService.submit(() -> {
                if (entry.entry().getName().endsWith(".class")) {
                    labelled[number] = repairClass(entry.contents());
                } else {
                    labelled[number] = entry.contents();
                }
            });
        }
        for (int i = 0; i < batchSize; i++) {
            try {
                futures[i].get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static byte[] labelClass(@Nullable Set<String> names, byte[] bytecode) {
        var reader = new ClassReader(bytecode);
        var writer = new ClassWriter(0);
        reader.accept(new MethodLabeller(names, writer), 0);
        return writer.toByteArray();
    }

    private static byte[] repairClass(byte[] bytecode) {
        RenameCollector collector = new RenameCollector();
        new ClassReader(bytecode).accept(collector, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        var writer = new ClassWriter(0);
        new ClassReader(bytecode).accept(new LambdaRenamer(collector.renames, collector.unknownNames, writer), 0);
        return writer.toByteArray();
    }

    public static void main(String[] args) {
        var exit = new CommandLine(new Main()).execute(args);
        System.exit(exit);
    }

    private record Entry(ZipEntry entry, byte[] contents) {}
}
