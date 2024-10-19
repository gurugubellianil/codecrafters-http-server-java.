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

        // Handle --directory argument to specify the file directory
        if (args.length == 2 && args[0].equalsIgnoreCase("--directory")) {
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
            if (line == null) return;

            String[] httpRequest = line.split(" ");
            String path = httpRequest[1];
            String userAgent = null;

            // Read headers to find the User-Agent
            String header;
            while ((header = reader.readLine()) != null && !header.isEmpty()) {
                if (header.startsWith("User-Agent:")) {
                    userAgent = header.split(": ")[1];
                }
            }

            // Handle root request
            if (path.equals("/")) {
                output.write("HTTP/1.1 200 OK\r\n\r\n".getBytes());

            // Handle /user-agent endpoint
            } else if (path.equals("/user-agent")) {
                if (userAgent != null) {
                    String response = String.format(
                            "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: %d\r\n\r\n%s",
                            userAgent.length(), userAgent);
                    output.write(response.getBytes());
                } else {
                    output.write("HTTP/1.1 400 Bad Request\r\n\r\n".getBytes());
                }

            // Handle /echo/{message} endpoint
            } else if (path.startsWith("/echo/")) {
                String message = path.split("/")[2];
                output.write(
                        ("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: " +
                                message.length() + "\r\n\r\n" + message)
                                .getBytes());

            // Handle /files/{filename} endpoint
            } else if (path.startsWith("/files/")) {
                String filename = path.substring(7);
                Path filePath = Paths.get(directory, filename);
                if (Files.exists(filePath)) {
                    byte[] fileBytes = Files.readAllBytes(filePath);
                    String response = String.format(
                            "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: %d\r\n\r\n",
                            fileBytes.length);
                    output.write(response.getBytes());
                    output.write(fileBytes);
                } else {
                    output.write("HTTP/1.1 404 Not Found\r\n\r\n".getBytes());
                }

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
