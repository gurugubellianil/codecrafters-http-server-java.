import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
  public static void main(String[] args) {
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
      String userAgent = null;

      // Read headers to find the User-Agent
      String header;
      while ((header = reader.readLine()) != null && !header.isEmpty()) {
        if (header.startsWith("User-Agent:")) {
          userAgent = header.split(": ")[1];
        }
      }

      // Handle requests
      if (HttpRequest[1].equals("/")) {
        output.write("HTTP/1.1 200 OK\r\n\r\n".getBytes());
      } else if (HttpRequest[1].equals("/user-agent")) {
        if (userAgent != null) {
          String response = String.format(
              "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: %d\r\n\r\n%s",
              userAgent.length(), userAgent);
          output.write(response.getBytes());
        } else {
          output.write("HTTP/1.1 400 Bad Request\r\n\r\n".getBytes());
        }
      } else if (HttpRequest[1].startsWith("/echo/")) {
        String queryParam = HttpRequest[1].split("/")[2];
        output.write(
            ("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: " +
                queryParam.length() + "\r\n\r\n" + queryParam)
                .getBytes());
      } else {
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
