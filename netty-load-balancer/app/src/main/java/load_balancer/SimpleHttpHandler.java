package load_balancer;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import lombok.extern.slf4j.Slf4j;

// HTTP 요청을 처리하는 핸들러
@Slf4j
public class SimpleHttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

	private static final String responseContent = "Hello from Netty Load Balancer";
	private final ConsistentHashing hashing;
	private final ProxyRequestHandler proxyRequestHandler;
	private final ConcurrentHashMap<String, Boolean> serverStatusMap;

	public SimpleHttpHandler(
		ConsistentHashing hashing,
		ProxyRequestHandler proxyRequestHandler,
		ConcurrentHashMap<String, Boolean> serverStatusMap
	) {
		this.hashing = hashing;
		this.proxyRequestHandler = proxyRequestHandler;
		this.serverStatusMap = serverStatusMap;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext context, FullHttpRequest request) {
		// 클라이언트의 IP를 사용
		InetSocketAddress socketAddress = (InetSocketAddress) context.channel().remoteAddress();
		String clientIp = socketAddress.getAddress().getHostAddress();
		Optional<String> optionalTarget = hashing.getServer(clientIp);

		if (optionalTarget.isEmpty()) {
			sendError(context, HttpResponseStatus.SERVICE_UNAVAILABLE);
			return;
		}

		String targetServer = optionalTarget.get();
		if (!serverStatusMap.getOrDefault(targetServer, false)) {
			sendError(context, HttpResponseStatus.SERVICE_UNAVAILABLE);
			return;
		}

		try {
			URI targetUri = new URI(targetServer + request.uri());
			log.info("Routing request to : {}", targetUri);

			// Netty를 사용해 백엔드 서버로 요청을 프록시
			proxyRequestHandler.forwardRequest(context, request, targetUri);
		} catch (URISyntaxException e) {
			sendError(context, HttpResponseStatus.INTERNAL_SERVER_ERROR);
		}

		FullHttpResponse response = new DefaultFullHttpResponse(
			HttpVersion.HTTP_1_1,
			HttpResponseStatus.OK,
			context.alloc().buffer().writeBytes(responseContent.getBytes(StandardCharsets.UTF_8))
		);
		response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
		response.headers().set(HttpHeaderNames.CONTENT_LENGTH, responseContent.length());
		context.writeAndFlush(response);
	}

	private void sendError(ChannelHandlerContext context, HttpResponseStatus status) {
		context.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status))
			.addListener(ChannelFutureListener.CLOSE);
	}
}
