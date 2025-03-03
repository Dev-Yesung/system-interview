package load_balancer;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

public class ProxyRequestHandler {

	private static final int UNASSIGNED_PORT = -1;
	private static final int DEFAULT_HTTP_PORT = 80;
	private static final String HOST_PORT_SEPARATOR = ":";
	private final EventLoopGroup eventLoopGroup = new NioEventLoopGroup();
	private final ConcurrentHashMap<String, ChannelFuture> connections = new ConcurrentHashMap<>();

	public void forwardRequest(ChannelHandlerContext context, FullHttpRequest request, URI targetUri) {
		String host = targetUri.getHost();
		int port = targetUri.getPort() == UNASSIGNED_PORT ? DEFAULT_HTTP_PORT : targetUri.getPort();

		ChannelFuture future = connections.computeIfAbsent(host + HOST_PORT_SEPARATOR + port, key -> {
			Bootstrap bootstrap = new Bootstrap();
			bootstrap.group(eventLoopGroup)
				.channel(NioSocketChannel.class)
				.handler(new ChannelInitializer<>() {
					@Override
					protected void initChannel(Channel channel) throws Exception {
						channel.pipeline().addLast(
							new HttpClientCodec(),
							new HttpObjectAggregator(65536),
							new ProxyResponseHandler(context));
					}
				});

			return bootstrap.connect(host, port);
		});

		future.addListener((ChannelFutureListener)f -> {
			if (!f.isSuccess()) {
				sendError(context, HttpResponseStatus.BAD_GATEWAY);
			}

			f.channel().writeAndFlush(request.retain());
		});
	}

	private void sendError(ChannelHandlerContext context, HttpResponseStatus status) {
		FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
		context.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
	}
}
