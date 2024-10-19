import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
  private static String directory = "/tmp"; // Default directory

  public static void main(String[] args) {
    // Check for --directory flag
    if (args.length == 2 && args[0].equals("--directory")) {
      directory = args[1];
    }

    final int port = 4221;
    final int maxThreads = 10;
    ExecutorService threadPool = Executors.newFixedThreadPool(maxThreads);

    try (ServerSocket serverSocket = new ServerSocket(port)) {
      serverSocket.setReuseAddress(true);
      System.out.println("Server is running on port " + port);

      while (true) {
        Socket clientSocket = serverSocket.accept();
        threadPool.submit(() -> handleRequest(clientSocket));
      }
    } catch (IOException e) {
      System.out.println("IOException: " + e.getMessage());
    }
  }

  private static void handleRequest(Socket clientSocket) {
    try (InputStream input = clientSocket.getInputStream();
         BufferedReader reader = new BufferedReader(new InputStreamReader(input));
         OutputStream output = clientSocket.getOutputStream()) {

      String line = reader.readLine();
      System.out.println("Request: " + line);
      String[] HttpRequest = line.split(" ");

      if (HttpRequest.length > 1) {
        String path = HttpRequest[1];
        
        if (path.equals("/")) {
          // Handle root ("/") path - return 200 OK with a simple response
          String responseHeaders = "HTTP/1.1 200 OK\r\n" +
                                   "Content-Type: text/plain\r\n" +
                                   "Content-Length: 0\r\n\r\n";  // Empty body
          output.write(responseHeaders.getBytes());
        } else if (path.startsWith("/files/")) {
          String filename = path.substring("/files/".length());
          handleFileRequest(output, filename);
        } else {
          output.write("HTTP/1.1 404 Not Found\r\n\r\n".getBytes());
        }
      } else {
        output.write("HTTP/1.1 400 Bad Request\r\n\r\n".getBytes());
      }

      output.flush();

    } catch (IOException e) {
      System.out.println("IOException while handling request: " + e.getMessage());
    } finally {
      try {
        clientSocket.close();
      } catch (IOException e) {
        System.out.println("IOException on socket close: " + e.getMessage());
      }
    }
  }

  private static void handleFileRequest(OutputStream output, String filename) {
    Path filePath = Paths.get(directory, filename);

    if (Files.exists(filePath)) {
      try {
        byte[] fileData = Files.readAllBytes(filePath);
        String responseHeaders = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: application/octet-stream\r\n" +
            "Content-Length: " + fileData.length + "\r\n\r\n";
        output.write(responseHeaders.getBytes());
        output.write(fileData);  // Write file content as response body
      } catch (IOException e) {
        System.out.println("IOException while reading file: " + e.getMessage());
      }
    } else {
      try {
        output.write("HTTP/1.1 404 Not Found\r\n\r\n".getBytes());
      } catch (IOException e) {
        System.out.println("IOException while sending 404 response: " + e.getMessage());
      }
    }
  }
}
