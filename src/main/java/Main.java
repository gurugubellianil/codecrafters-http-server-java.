import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    private static String directory = null;

    public static void main(String[] args) {
        final int port = 4221;
        final int maxThreads = 10;
        ExecutorService threadPool = Executors.newFixedThreadPool(maxThreads);

        // Check for the --directory argument
        if (args.length == 2 && args[0].equalsIgnoreCase("--directory")) {
            directory = args[1];
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

            // Handle echo requests
            if (httpRequest[1].startsWith("/echo/")) {
                handleEchoRequest(httpRequest[1], output);
            } else if (httpRequest[1].startsWith("/files/")) {
                handleFileRequest(httpRequest[1], output);
            } else {
                // Existing request handling
                handleOtherRequests(httpRequest, reader, output);
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

    private static void handleFileRequest(String filePath, OutputStream output) throws IOException {
        String fileName = filePath.substring(7); // Remove "/files/"
        Path path = Paths.get(directory, fileName);

        if (Files.exists(path)) {
            byte[] fileBytes = Files.readAllBytes(path);
            String response = "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: " +
                    fileBytes.length + "\r\n\r\n";
            output.write(response.getBytes());
            output.write(fileBytes);
        } else {
            output.write("HTTP/1.1 404 Not Found\r\n\r\n".getBytes());
        }
    }

    private static void handleOtherRequests(String[] httpRequest, BufferedReader reader, OutputStream output) throws IOException {
        // Existing request handling (for /, /user-agent)
        String userAgent = null;
        String header;
        while ((header = reader.readLine()) != null && !header.isEmpty()) {
            if (header.startsWith("User-Agent:")) {
                userAgent = header.split(": ")[1];
            }
        }

        if (httpRequest[1].equals("/")) {
            output.write("HTTP/1.1 200 OK\r\n\r\n".getBytes());
        } else if (httpRequest[1].equals("/user-agent")) {
            if (userAgent != null) {
                String response = String.format(
                        "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: %d\r\n\r\n%s",
                        userAgent.length(), userAgent);
                output.write(response.getBytes());
            } else {
                output.write("HTTP/1.1 400 Bad Request\r\n\r\n".getBytes());
            }
        } else {
            output.write("HTTP/1.1 404 Not Found\r\n\r\n".getBytes());
        }
    }

    private static void handleEchoRequest(String path, OutputStream output) throws IOException {
        String message = path.substring(6); // Remove "/echo/"
        String response = String.format(
                "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: %d\r\n\r\n%s",
                message.length(), message);
        output.write(response.getBytes());
    }
}
