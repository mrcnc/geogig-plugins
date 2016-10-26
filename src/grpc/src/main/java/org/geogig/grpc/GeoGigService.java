package org.geogig.grpc;

import io.grpc.stub.StreamObserver;
import org.locationtech.geogig.plumbing.ResolveRepositoryName;
import org.locationtech.geogig.porcelain.InitOp;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.repository.RepositoryResolver;

/**
 * This is where we override the methods and do the actual work of the service.
 */
public class GeoGigService extends GeoGigServiceGrpc.GeoGigServiceImplBase {


    public GeoGigService() {
    }

    @Override
    public void createRepo(CreateRepoRequest req, StreamObserver<CreateRepoResponse> responseObserver) {
        String repoName = req.getRepoName();


        InitOp command = geogig.command(InitOp.class);


        Repository newRepo = command.call();

        try {
            final String repositoryName = RepositoryResolver.load(newRepo.getLocation())
                    .command(ResolveRepositoryName.class).call();
            // send a response on the response stream
            CreateRepoResponse res = CreateRepoResponse.newBuilder()
                    .setSuccess(true)
                    .setRepoName(repositoryName)
                    .build();
            responseObserver.onNext(res);
        } catch (RepositoryConnectionException e) {
            e.printStackTrace();
            responseObserver.onError(e);
        }
        responseObserver.onCompleted();
    }

    @Override
    public void importData(ImportRequest req, StreamObserver<ImportResponse> responseObserver) {
        String dataSourceType = req.getDataSourceType();
        String treePath = req.getDestinationTreePath();
        DataSource dataSource = req.getDataSource();

        // use plumbing/porcelain commands to import data....but don't call web api
        String sourceName = dataSourceType.equals("gpkg") ? dataSource.getSourceName() : dataSource.getDatabase();

        // send status updates response stream
        for (int idx = 0; idx < 100; idx++) {
            float percentage = ((float) idx) / 100;
            String status = idx == 100 ?
                    "Completed" :
                    String.format("In progress %2.02f", percentage);
            Task importTask = Task.newBuilder()
                    .setTaskId(1)
                    .setTransactionId("some id")
                    .setStatus(status)
                    .build();
            ImportResponse res = ImportResponse.newBuilder()
                    .setSuccess(true)
                    .setRepoName(sourceName)
                    .setTask(importTask)
                    .build();
            responseObserver.onNext(res);
        }
        responseObserver.onCompleted();
    }

    @Override
    public void exportGeoPackage(ExportRequest request, StreamObserver<ExportResponse> responseObserver) {
        // send status updates response stream
        for (int idx = 0; idx < 100; idx++) {
            float percentage = ((float) idx) / 100;
            String status = idx == 100 ?
                    "Completed" :
                    String.format("In progress %2.02f", percentage);
            Task importTask = Task.newBuilder()
                    .setTaskId(2)
                    .setTransactionId("some id")
                    .setStatus(status)
                    .build();
            ExportResponse res = ExportResponse.newBuilder()
                    .setSuccess(true)
                    .setTask(importTask)
                    .build();
            responseObserver.onNext(res);
        }
        responseObserver.onCompleted();

    }

    @Override
    public StreamObserver<SyncFeatureRequest> syncFeatures(StreamObserver<SyncFeatureResponse> responseObserver) {
        return new StreamObserver<SyncFeatureRequest>() {
            @Override
            public void onNext(SyncFeatureRequest req) {
                SyncFeatureResponse res = SyncFeatureResponse.newBuilder()
                        .setSuccess(true)
                        .setFeatureId(req.getOperation().getFeatureId())
                        .setCommitId(req.getCommitId())
                        .build();
                responseObserver.onNext(res);
            }

            @Override
            public void onError(Throwable t) {

            }

            @Override
            public void onCompleted() {

            }
        };
    }
}
