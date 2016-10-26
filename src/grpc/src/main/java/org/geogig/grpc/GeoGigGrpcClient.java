package org.geogig.grpc;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple client that uses stubs to call methods on a {@link GeoGigGrpcServer}.
 */
public class GeoGigGrpcClient {

    private static final Logger logger = Logger.getLogger(GeoGigGrpcClient.class.getName());

    private final ManagedChannel channel;
    private final GeoGigServiceGrpc.GeoGigServiceBlockingStub blockingStub;
    private final GeoGigServiceGrpc.GeoGigServiceStub asyncStub;

    /**
     * Construct client connecting to GeoGigServer at {@code host:port}.
     */
    public GeoGigGrpcClient(String host, int port) {
        // create a gRPC channel to the server
        channel = ManagedChannelBuilder.forAddress(host, port)
                // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
                // needing certificates.
                .usePlaintext(true)
                .build();
        blockingStub = GeoGigServiceGrpc.newBlockingStub(channel);
        asyncStub = GeoGigServiceGrpc.newStub(channel);
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    public void createRepo(String repoName) {
        logger.info("Attempting to create repo " + repoName + " ...");

        CreateRepoRequest req = CreateRepoRequest.newBuilder()
                .setRepoName(repoName)
                .build();
        try {
            CreateRepoResponse res = blockingStub.createRepo(req);
            logger.info("newly created repo name: " + res.getRepoName());
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return;
        }
    }

    public void importData(String repoName, String treePath) throws InterruptedException {
        logger.info("Attempting to import data into repo " + repoName + " ...");

        final CountDownLatch finishLatch = new CountDownLatch(1);

        ImportRequest req = ImportRequest.newBuilder()
                .setDestinationTreePath(treePath)
                .setDataSourceType("gpkg")
                .build();

        // make the import call and subscribe to response events
        asyncStub.importData(req, new StreamObserver<ImportResponse>() {
            @Override
            public void onNext(ImportResponse response) {
                logger.info("ImportResponse:\n" + response.toString());
            }

            @Override
            public void onError(Throwable t) {
                t.printStackTrace();
                logger.info("error!" + t.getMessage());
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                logger.info("import completed!");
                finishLatch.countDown();
            }
        });

        finishLatch.await(1, TimeUnit.MINUTES);
    }


    /**
     * Exports a snapshot of a GeoGig repository as a GeoPackage file for use by mobile devices.
     * <p/>
     * {@link http://geogig.org/docs/interaction/geopackage-import-export.html#geopackage-export}
     *
     * @param repoName name of repository to export from
     * @param refSpec  branch name or commit identifier to export from
     * @param bbox     bounding box filter as minx,miny,maxx,maxy,<SRS>
     */
    public void exportGeoPackage(String repoName, String refSpec, String bbox) throws InterruptedException {
        logger.info("Attempting to export data from repo " + repoName + " ...");

        final CountDownLatch finishLatch = new CountDownLatch(1);

        ExportRequest req = ExportRequest.newBuilder()
                .setRepoName(repoName)
                .setRefSpec(refSpec)
                .setBbox(bbox)
                .build();

        asyncStub.exportGeoPackage(req, new StreamObserver<ExportResponse>() {
            @Override
            public void onNext(ExportResponse response) {
                logger.info("ExportResponse:\n" + response.toString());
            }

            @Override
            public void onError(Throwable t) {
                t.printStackTrace();
                logger.info("error!" + t.getMessage());
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                logger.info("export completed!");
                finishLatch.countDown();
            }
        });

        finishLatch.await(1, TimeUnit.MINUTES);
    }


    public void syncFeatures(String repoName, String commitId, String workingTree, Set<FeatureOperation> operations)
            throws InterruptedException {
        logger.info("Attempting to sync features to repo " + repoName + " ...");

        final CountDownLatch finishLatch = new CountDownLatch(operations.size());

        StreamObserver<SyncFeatureResponse> responseObserver = new StreamObserver<SyncFeatureResponse>() {
            @Override
            public void onNext(SyncFeatureResponse response) {
                logger.info("SyncFeatureResponse:\n" + response.toString());
                finishLatch.countDown();
            }

            @Override
            public void onError(Throwable t) {
                t.printStackTrace();
                logger.info("error!" + t.getMessage());
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                logger.info("feature synced successfully!");
                finishLatch.countDown();
            }
        };

        StreamObserver<SyncFeatureRequest> requestObserver = asyncStub.syncFeatures(responseObserver);

        for (FeatureOperation operation : operations) {
            SyncFeatureRequest req = SyncFeatureRequest.newBuilder()
                    .setRepoName(repoName)
                    .setCommitId(commitId)
                    .setTree(workingTree)
                    .setOperation(operation)
                    .build();
            requestObserver.onNext(req);
        }

        requestObserver.onCompleted();

        finishLatch.await(1, TimeUnit.MINUTES);
    }


    public static void main(String[] args) throws Exception {
        GeoGigGrpcClient client = new GeoGigGrpcClient("localhost", 50051);
        try {
            String command = null;
            if (args.length > 0) {
                command = args[0];
            }
            String repoName = "my new repo";
            if (command.equals("createRepo")) {
                client.createRepo(repoName);
            }
            if (command.equals("importData")) {
                client.importData(repoName, "master");
            }
            if (command.equals("export")) {
                client.exportGeoPackage(repoName, "master", "");
            }
            if (command.equals("sync")) {
                client.syncFeatures(repoName, "commitId", "workingTree", getFeatureOperations());
            }
        } finally {
            client.shutdown();
        }
    }

    private static Set<FeatureOperation> getFeatureOperations() {
        TreeSet treeSet = new TreeSet(new Comparator<FeatureOperation>() {
            @Override
            public int compare(FeatureOperation o1, FeatureOperation o2) {
                return Instant.parse(o1.getAuditTimestamp()).compareTo(Instant.parse(o2.getAuditTimestamp()));
            }
        });

        Struct featureProps1 = FeatureOperation.newBuilder().getPropertiesBuilder()
                .putFields("someKey", Value.newBuilder().setBoolValue(true).build())
                .putFields("anotherKey", Value.newBuilder().setStringValue("some value").build())
                .build();

        Struct featureProps2 = FeatureOperation.newBuilder().getPropertiesBuilder()
                .putFields("someKey", Value.newBuilder().setBoolValue(false).build())
                .putFields("anotherKey", Value.newBuilder().setStringValue("another value").build())
                .build();

        FeatureOperation op1 = FeatureOperation.newBuilder()
                .setFeatureId("fid-20b2075a_156913f10d7_1ab")
                .setAuditOp(3)
                .setAuditTimestamp("2016-08-15T00:00:00Z")
                .setProperties(featureProps1)
                .build();
        FeatureOperation op2 = FeatureOperation.newBuilder()
                .setFeatureId("fid-20b2075a_156913f10d7_-77")
                .setAuditOp(2)
                .setAuditTimestamp("2016-08-16T15:36:52Z")
                .setProperties(featureProps2)
                .build();

        treeSet.add(op1);
        treeSet.add(op2);

        return Collections.synchronizedSortedSet(treeSet);
    }
}
