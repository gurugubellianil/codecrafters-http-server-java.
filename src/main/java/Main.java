import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
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
import java.util.zip.GZIPOutputStream;

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
            String acceptEncoding = null;

            // Read headers to find Accept-Encoding
            String header;
            while ((header = reader.readLine()) != null && !header.isEmpty()) {
                if (header.startsWith("Accept-Encoding:")) {
                    acceptEncoding = header.split(": ")[1];
                }
            }

            if (httpRequest[0].equals("GET") && httpRequest[1].startsWith("/echo/")) {
                handleEchoRequest(httpRequest[1], output, acceptEncoding);
            } else if (httpRequest[0].equals("POST") && httpRequest[1].startsWith("/files/")) {
                handleFileRequest(httpRequest[1], reader, output);
            } else {
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



    private static void handleFileRequest(String filePath, BufferedReader reader, OutputStream output) throws IOException {
        String fileName = filePath.substring(7); // Remove "/files/"
        Path path = Paths.get(directory, fileName);
        
        // Read Content-Length
        int contentLength = -1; // Default to an invalid value
        String header;
        while ((header = reader.readLine()) != null && !header.isEmpty()) {
            if (header.startsWith("Content-Length:")) {
                contentLength = Integer.parseInt(header.split(": ")[1]);
            }
        }

        // Read the body based on the content length
        char[] body = new char[contentLength];
        reader.read(body, 0, contentLength);
        String requestBody = new String(body);

        // Create file
        Files.write(path, requestBody.getBytes());

        // Respond with a 201 Created status
        output.write("HTTP/1.1 201 Created\r\n\r\n".getBytes());
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

    private static void handleEchoRequest(String path, OutputStream output, String acceptEncoding) throws IOException {
        String message = path.substring(6);
        byte[] responseBodyBytes = message.getBytes();
        boolean acceptsGzip = acceptEncoding != null && acceptEncoding.contains("gzip");

        if (acceptsGzip) {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream)) {
                gzipOutputStream.write(responseBodyBytes);
            }
            byte[] compressedResponseBody = byteArrayOutputStream.toByteArray();
            String response = String.format(
                    "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: %d\r\nContent-Encoding: gzip\r\n\r\n",
                    compressedResponseBody.length);
            output.write(response.getBytes());
            output.write(compressedResponseBody);
        } else {
            String response = String.format(
                    "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: %d\r\n\r\n%s",
                    responseBodyBytes.length, message);
            output.write(response.getBytes());
        }
    }

}
