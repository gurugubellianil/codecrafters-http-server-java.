import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
  private static String directory = null;

  public static void main(String[] args) {
    final int port = 4221;
    final int maxThreads = 10;
    ExecutorService threadPool = Executors.newFixedThreadPool(maxThreads);

    // Get the directory from command line arguments
    if (args.length == 2 && "--directory".equals(args[0])) {
      directory = args[1];
    } else {
      System.out.println("Usage: java Main --directory <directory_path>");
      return;
    }

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
      String[] httpRequest = line.split(" ");
      String userAgent = null;

      // Read headers to find the User-Agent
      String header;
      while ((header = reader.readLine()) != null && !header.isEmpty()) {
        if (header.startsWith("User-Agent:")) {
          userAgent = header.split(": ")[1];
        }
      }

      // Handle root request
      if (httpRequest[1].equals("/")) {
        output.write("HTTP/1.1 200 OK\r\n\r\n".getBytes());
      } 
      // Handle User-Agent endpoint
      else if (httpRequest[1].equals("/user-agent")) {
        if (userAgent != null) {
          String response = String.format(
              "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: %d\r\n\r\n%s",
              userAgent.length(), userAgent);
          output.write(response.getBytes());
        } else {
          output.write("HTTP/1.1 400 Bad Request\r\n\r\n".getBytes());
        }
      } 
      // Handle echo endpoint
      else if (httpRequest[1].startsWith("/echo/")) {
        String queryParam = httpRequest[1].split("/")[2];
        output.write(
            ("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: " +
                queryParam.length() + "\r\n\r\n" + queryParam)
                .getBytes());
      } 
      // Handle /files/{filename} endpoint
      else if (httpRequest[1].startsWith("/files/")) {
        String filename = httpRequest[1].substring(7); // Remove "/files/"
        Path filePath = Paths.get(directory, filename);

        if (Files.exists(filePath)) {
          byte[] fileBytes = Files.readAllBytes(filePath);
          String response = "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\n" +
                            "Content-Length: " + fileBytes.length + "\r\n\r\n";
          output.write(response.getBytes());
          output.write(fileBytes);
        } else {
          output.write("HTTP/1.1 404 Not Found\r\n\r\n".getBytes());
        }
      } 
      // Handle unknown requests
      else {
        output.write("HTTP/1.1 404 Not Found\r\n\r\n".getBytes());
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
}
