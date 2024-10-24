package springIoc.utils;

import springIoc.io.InputStreamCallback;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;


public class ClassPathUtils {

    public static <T> T readInputStream(String path, InputStreamCallback<T> inputStreamCallback) {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        try (InputStream input = getContextClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new FileNotFoundException("File not found in classpath: " + path);
            }
            return inputStreamCallback.doWithInputStream(input);
        } catch (IOException e) {
            e.printStackTrace();
            throw new UncheckedIOException(e);
        }
    }

    public static String readString(String path) {
        return readInputStream(path, input -> {
            StringBuilder stringBuilder = new StringBuilder();
            byte[] buffer = new byte[1024];
            int bytesRead;

            try {
                while ((bytesRead = input.read(buffer)) != -1) {
                    stringBuilder.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to read input stream", e);
            }

            return stringBuilder.toString();
        });
    }

    static ClassLoader getContextClassLoader() {
        ClassLoader cl = null;
        cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = ClassPathUtils.class.getClassLoader();
        }
        return cl;
    }
}