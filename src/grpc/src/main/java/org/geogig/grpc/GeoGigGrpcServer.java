package org.geogig.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * A gRPC server that manages startup/shutdown of a {@link GeoGigService}.
 */
public class GeoGigGrpcServer {
  private static final Logger logger = Logger.getLogger(GeoGigGrpcServer.class.getName());

  /* The port on which the server should run */
  private int port = 50051;
  private Server server;

  public void start() throws IOException {
    server = ServerBuilder.forPort(port)
            .addService(new GeoGigService())
            .build()
            .start();

    logger.info("Server started, listening on " + port);
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        // Use stderr here since the logger may have been reset by its JVM shutdown hook.
        System.err.println("*** shutting down gRPC server since JVM is shutting down");
        GeoGigGrpcServer.this.stop();
        System.err.println("*** server shut down");
      }
    });
  }

  public void stop() {
    if (server != null) {
      server.shutdown();
    }
  }

  /**
   * Await termination on the main thread since the grpc library uses daemon threads.
   */
  public void blockUntilShutdown() throws InterruptedException {
    if (server != null) {
      server.awaitTermination();
    }
  }

  /**
   * Main launches the server from the command line.
   */
  public static void main(String[] args) throws IOException, InterruptedException {
    final GeoGigGrpcServer server = new GeoGigGrpcServer();
    server.start();
    server.blockUntilShutdown();
  }

}
