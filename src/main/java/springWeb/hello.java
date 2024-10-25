package springWeb;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class hello {
    public static void main(String[] args) {
        Path path = Paths.get("G:\\A.源码\\Spring-mini\\src\\test\\resources\\WEB-INF\\templates\\register.html");
        File file =new File("G:\\A.源码\\Spring-mini\\src\\test\\resources\\WEB-INF\\templates\\register.html");
        if (file.exists()) {
            try {
                System.out.println("Size: " + Files.size(path) + " bytes");
                System.out.println("Last Modified Time: " + Files.getLastModifiedTime(path));
                System.out.println("Is Readable: " + Files.isReadable(path));
                System.out.println("Is Writable: " + Files.isWritable(path));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("File does not exist.");
        }
    }
}
