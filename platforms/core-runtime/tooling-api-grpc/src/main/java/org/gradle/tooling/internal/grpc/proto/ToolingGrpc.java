package org.gradle.tooling.internal.grpc.proto;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@io.grpc.stub.annotations.GrpcGenerated
public final class ToolingGrpc {

  private ToolingGrpc() {}

  public static final String SERVICE_NAME = "gradle.tooling.grpc.Tooling";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<org.gradle.tooling.internal.grpc.proto.BuildRequest,
      org.gradle.tooling.internal.grpc.proto.BuildEvent> getRunBuildMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "RunBuild",
      requestType = org.gradle.tooling.internal.grpc.proto.BuildRequest.class,
      responseType = org.gradle.tooling.internal.grpc.proto.BuildEvent.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<org.gradle.tooling.internal.grpc.proto.BuildRequest,
      org.gradle.tooling.internal.grpc.proto.BuildEvent> getRunBuildMethod() {
    io.grpc.MethodDescriptor<org.gradle.tooling.internal.grpc.proto.BuildRequest, org.gradle.tooling.internal.grpc.proto.BuildEvent> getRunBuildMethod;
    if ((getRunBuildMethod = ToolingGrpc.getRunBuildMethod) == null) {
      synchronized (ToolingGrpc.class) {
        if ((getRunBuildMethod = ToolingGrpc.getRunBuildMethod) == null) {
          ToolingGrpc.getRunBuildMethod = getRunBuildMethod =
              io.grpc.MethodDescriptor.<org.gradle.tooling.internal.grpc.proto.BuildRequest, org.gradle.tooling.internal.grpc.proto.BuildEvent>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "RunBuild"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.gradle.tooling.internal.grpc.proto.BuildRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.gradle.tooling.internal.grpc.proto.BuildEvent.getDefaultInstance()))
              .setSchemaDescriptor(new ToolingMethodDescriptorSupplier("RunBuild"))
              .build();
        }
      }
    }
    return getRunBuildMethod;
  }

  private static volatile io.grpc.MethodDescriptor<org.gradle.tooling.internal.grpc.proto.ModelRequest,
      org.gradle.tooling.internal.grpc.proto.ModelResponse> getQueryModelMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "QueryModel",
      requestType = org.gradle.tooling.internal.grpc.proto.ModelRequest.class,
      responseType = org.gradle.tooling.internal.grpc.proto.ModelResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<org.gradle.tooling.internal.grpc.proto.ModelRequest,
      org.gradle.tooling.internal.grpc.proto.ModelResponse> getQueryModelMethod() {
    io.grpc.MethodDescriptor<org.gradle.tooling.internal.grpc.proto.ModelRequest, org.gradle.tooling.internal.grpc.proto.ModelResponse> getQueryModelMethod;
    if ((getQueryModelMethod = ToolingGrpc.getQueryModelMethod) == null) {
      synchronized (ToolingGrpc.class) {
        if ((getQueryModelMethod = ToolingGrpc.getQueryModelMethod) == null) {
          ToolingGrpc.getQueryModelMethod = getQueryModelMethod =
              io.grpc.MethodDescriptor.<org.gradle.tooling.internal.grpc.proto.ModelRequest, org.gradle.tooling.internal.grpc.proto.ModelResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "QueryModel"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.gradle.tooling.internal.grpc.proto.ModelRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.gradle.tooling.internal.grpc.proto.ModelResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ToolingMethodDescriptorSupplier("QueryModel"))
              .build();
        }
      }
    }
    return getQueryModelMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static ToolingStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ToolingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ToolingStub>() {
        @java.lang.Override
        public ToolingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ToolingStub(channel, callOptions);
        }
      };
    return ToolingStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static ToolingBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ToolingBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ToolingBlockingStub>() {
        @java.lang.Override
        public ToolingBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ToolingBlockingStub(channel, callOptions);
        }
      };
    return ToolingBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static ToolingFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ToolingFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ToolingFutureStub>() {
        @java.lang.Override
        public ToolingFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ToolingFutureStub(channel, callOptions);
        }
      };
    return ToolingFutureStub.newStub(factory, channel);
  }

  /**
   */
  public static abstract class ToolingImplBase implements io.grpc.BindableService {

    /**
     * <pre>
     * Run a build. Output streams as it happens; the final BuildEvent is the result.
     * </pre>
     */
    public void runBuild(org.gradle.tooling.internal.grpc.proto.BuildRequest request,
        io.grpc.stub.StreamObserver<org.gradle.tooling.internal.grpc.proto.BuildEvent> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRunBuildMethod(), responseObserver);
    }

    /**
     * <pre>
     * Query information about a build (the "C" slice). Unary request/response.
     * </pre>
     */
    public void queryModel(org.gradle.tooling.internal.grpc.proto.ModelRequest request,
        io.grpc.stub.StreamObserver<org.gradle.tooling.internal.grpc.proto.ModelResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getQueryModelMethod(), responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getRunBuildMethod(),
            io.grpc.stub.ServerCalls.asyncServerStreamingCall(
              new MethodHandlers<
                org.gradle.tooling.internal.grpc.proto.BuildRequest,
                org.gradle.tooling.internal.grpc.proto.BuildEvent>(
                  this, METHODID_RUN_BUILD)))
          .addMethod(
            getQueryModelMethod(),
            io.grpc.stub.ServerCalls.asyncUnaryCall(
              new MethodHandlers<
                org.gradle.tooling.internal.grpc.proto.ModelRequest,
                org.gradle.tooling.internal.grpc.proto.ModelResponse>(
                  this, METHODID_QUERY_MODEL)))
          .build();
    }
  }

  /**
   */
  public static final class ToolingStub extends io.grpc.stub.AbstractAsyncStub<ToolingStub> {
    private ToolingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ToolingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ToolingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Run a build. Output streams as it happens; the final BuildEvent is the result.
     * </pre>
     */
    public void runBuild(org.gradle.tooling.internal.grpc.proto.BuildRequest request,
        io.grpc.stub.StreamObserver<org.gradle.tooling.internal.grpc.proto.BuildEvent> responseObserver) {
      io.grpc.stub.ClientCalls.asyncServerStreamingCall(
          getChannel().newCall(getRunBuildMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Query information about a build (the "C" slice). Unary request/response.
     * </pre>
     */
    public void queryModel(org.gradle.tooling.internal.grpc.proto.ModelRequest request,
        io.grpc.stub.StreamObserver<org.gradle.tooling.internal.grpc.proto.ModelResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getQueryModelMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   */
  public static final class ToolingBlockingStub extends io.grpc.stub.AbstractBlockingStub<ToolingBlockingStub> {
    private ToolingBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ToolingBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ToolingBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Run a build. Output streams as it happens; the final BuildEvent is the result.
     * </pre>
     */
    public java.util.Iterator<org.gradle.tooling.internal.grpc.proto.BuildEvent> runBuild(
        org.gradle.tooling.internal.grpc.proto.BuildRequest request) {
      return io.grpc.stub.ClientCalls.blockingServerStreamingCall(
          getChannel(), getRunBuildMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Query information about a build (the "C" slice). Unary request/response.
     * </pre>
     */
    public org.gradle.tooling.internal.grpc.proto.ModelResponse queryModel(org.gradle.tooling.internal.grpc.proto.ModelRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getQueryModelMethod(), getCallOptions(), request);
    }
  }

  /**
   */
  public static final class ToolingFutureStub extends io.grpc.stub.AbstractFutureStub<ToolingFutureStub> {
    private ToolingFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ToolingFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ToolingFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Query information about a build (the "C" slice). Unary request/response.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<org.gradle.tooling.internal.grpc.proto.ModelResponse> queryModel(
        org.gradle.tooling.internal.grpc.proto.ModelRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getQueryModelMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_RUN_BUILD = 0;
  private static final int METHODID_QUERY_MODEL = 1;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final ToolingImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(ToolingImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_RUN_BUILD:
          serviceImpl.runBuild((org.gradle.tooling.internal.grpc.proto.BuildRequest) request,
              (io.grpc.stub.StreamObserver<org.gradle.tooling.internal.grpc.proto.BuildEvent>) responseObserver);
          break;
        case METHODID_QUERY_MODEL:
          serviceImpl.queryModel((org.gradle.tooling.internal.grpc.proto.ModelRequest) request,
              (io.grpc.stub.StreamObserver<org.gradle.tooling.internal.grpc.proto.ModelResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  private static abstract class ToolingBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    ToolingBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return org.gradle.tooling.internal.grpc.proto.ToolingProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("Tooling");
    }
  }

  private static final class ToolingFileDescriptorSupplier
      extends ToolingBaseDescriptorSupplier {
    ToolingFileDescriptorSupplier() {}
  }

  private static final class ToolingMethodDescriptorSupplier
      extends ToolingBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    ToolingMethodDescriptorSupplier(String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (ToolingGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new ToolingFileDescriptorSupplier())
              .addMethod(getRunBuildMethod())
              .addMethod(getQueryModelMethod())
              .build();
        }
      }
    }
    return result;
  }
}
