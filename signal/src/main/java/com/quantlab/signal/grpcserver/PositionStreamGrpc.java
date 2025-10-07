package com.quantlab.signal.grpcserver;

import com.quantlab.common.entity.Position;
import com.quantlab.common.repository.PositionRepository;
import com.quantlab.signal.service.PositionResponseMapper;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


@Service
public class PositionStreamGrpc {

    private static final Logger logger = LogManager.getLogger(PositionStreamGrpc.class);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Autowired private PositionRepository positionRepository;
    @Autowired private PositionResponseMapper positionResponseMapper;

    @GrpcClient("grpc-quantlab-service-xts")
    private com.market.proto.xts.PositionServiceGrpc.PositionServiceStub xtsAsyncStub;

    @GrpcClient("grpc-quantlab-service-tr")
    private com.market.proto.tr.PositionServiceGrpc.PositionServiceStub trAsyncStub;

    @PostConstruct
    public void start() {
        // You can start one or both based on conditions
        streamTrPositionData();
    }

    public void streamXtsPositionData() {
        com.market.proto.xts.PositionStreamRequest request = com.market.proto.xts.PositionStreamRequest.newBuilder().build();
        logger.info("Starting XTS position stream...");

        xtsAsyncStub.streamPositionData(request, new StreamObserver<>() {
            @Override
            public void onNext(com.market.proto.xts.PositionResponseStream response) {
                Position position = positionResponseMapper.mapToXtsPosition(response);
                logger.info("Received XTS Position response: {}", position);
                savePositionData(List.of(position));
            }

            @Override
            public void onError(Throwable t) {
                logger.error("XTS stream error: {}", t.getMessage(), t);
                retryXtsPositionStream();
            }

            @Override
            public void onCompleted() {
                logger.info("XTS Position stream completed.");
                retryXtsPositionStream();
            }
        });
    }

    public void streamTrPositionData() {
        com.market.proto.tr.PositionStreamRequest request = com.market.proto.tr.PositionStreamRequest.newBuilder().build();
        logger.info("Starting TR position stream...");

        trAsyncStub.streamPositionData(request, new StreamObserver<>() {
            @Override
            public void onNext(com.market.proto.tr.PositionResponseStream response) {
                try {
                    List<Position> position = positionResponseMapper.mapToTrPosition(response);
                    savePositionData(position);
                } catch (Exception e) {
                    logger.error("Error mapping TR Position response: {}", e.getMessage(), e);
                }
            }

            @Override
            public void onError(Throwable t) {
                logger.error("TR stream error: {}", t.getMessage());
                retryTrPositionStream();
            }

            @Override
            public void onCompleted() {
                logger.info("TR stream completed.");
                retryTrPositionStream();
            }
        });
    }

    private void savePositionData(List<Position> positions) {
        if (positions != null && !positions.isEmpty()) {
            try {
                positionRepository.saveAll(positions);
            } catch (RuntimeException e) {
                logger.error("Failed to save position data: {}", e.getMessage(), e);
            }
        }
    }

    private void retryXtsPositionStream() {
        scheduler.schedule(this::streamXtsPositionData, 2, TimeUnit.SECONDS);
    }

    private void retryTrPositionStream() {
        scheduler.schedule(this::streamTrPositionData, 2, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }
}


