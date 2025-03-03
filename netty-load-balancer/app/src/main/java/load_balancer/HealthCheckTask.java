package load_balancer;

import java.net.URI;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;

public class HealthCheckTask {

	private final EventLoopGroup group = new NioEventLoopGroup();
	private final List<String> servers;
	private final ConcurrentHashMap<String, Boolean> serverStatusMap;

	public HealthCheckTask(
		List<String> servers,
		ConcurrentHashMap<String, Boolean> serverStatusMap
	) {
		this.servers = servers;
		this.serverStatusMap = serverStatusMap;
	}

	public void startHealthCheck() {
		for (String server : servers) {
			URI healthCheckUri;
			try {
				healthCheckUri = new URI(server + "/health");
			} catch (Exception e) {
				continue;
			}

			checkServerHealth(healthCheckUri);
		}

		// 10초마다 헬스 체크를 다시 실행
		group.schedule(this::startHealthCheck, 10, java.util.concurrent.TimeUnit.SECONDS);
	}

	private void checkServerHealth(URI healthCheckUri) {
		Bootstrap bootstrap = new Bootstrap();
		bootstrap.group(group)
			.channel(NioSocketChannel.class)
			.handler(new ChannelInitializer<>() {
				@Override
				protected void initChannel(Channel channel) {
					channel.pipeline().addLast(
						new HttpClientCodec(),
						new HttpObjectAggregator(65536),
						new HealthCheckHandler(healthCheckUri)
					);
				}
			});

		bootstrap.connect(healthCheckUri.getHost(), healthCheckUri.getPort())
			.addListener((ChannelFutureListener)future -> {
				if (!future.isSuccess()) {
					serverStatusMap.put(healthCheckUri.toString(), false);
				}
			});
	}

	private class HealthCheckHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

		private final URI healthCheckUri;

		public HealthCheckHandler(URI healthCheckUri) {
			this.healthCheckUri = healthCheckUri;
		}

		@Override
		protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) {
			if (msg.status().equals(HttpResponseStatus.OK)) {
				serverStatusMap.put(healthCheckUri.toString(), true);
			} else {
				serverStatusMap.put(healthCheckUri.toString(), false);
			}
		}
	}
}
