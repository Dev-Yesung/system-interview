package load_balancer;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Application {

	private static final int DEFAULT_PORT = 8080;
	private static final List<String> SERVERS = List.of(
		"http://localhost:9001",
		"http://localhost:9002",
		"http://localhost:9003"
	);
	private static final ConcurrentHashMap<String, Boolean> SERVER_STATUS_MAP = new ConcurrentHashMap<>();

	public void start() throws InterruptedException {
		// 네트워크 이벤트를 처리하는 스레드 그룹
		EventLoopGroup bossGroup = new NioEventLoopGroup(1);
		EventLoopGroup workerGroup = new NioEventLoopGroup();

		ConsistentHashing consistentHashing = new ConsistentHashing(SERVERS, 10);
		ProxyRequestHandler proxyRequestHandler = new ProxyRequestHandler();

		// 헬스 체크 시작
		HealthCheckTask healthCheckTask = new HealthCheckTask(SERVERS, SERVER_STATUS_MAP);
		healthCheckTask.startHealthCheck();

		try {
			// Netty 서버를 설정하고 바인딩하는 부트스트랩 객체
			ServerBootstrap bootstrap = new ServerBootstrap();
			bootstrap.group(bossGroup, workerGroup)
				.channel(NioServerSocketChannel.class)
				.childHandler(new ChannelInitializer<>() {
					// 클라이언트 요청을 처리할 핸들러 초기화
					@Override
					protected void initChannel(Channel channel) {
						channel.pipeline().addLast(
							new HttpServerCodec(),
							new HttpObjectAggregator(65536),
							// consistent hash initialize and delegate
							new SimpleHttpHandler(consistentHashing, proxyRequestHandler, SERVER_STATUS_MAP)
						);
					}
				});
			ChannelFuture future = bootstrap.bind(DEFAULT_PORT).sync();
			log.info("Consistent Hash Load Balancer started on port {}", DEFAULT_PORT);
			future.channel().closeFuture().sync();
		} finally {
			bossGroup.shutdownGracefully();
			workerGroup.shutdownGracefully();
		}
	}

	public static void main(String[] args) throws InterruptedException {
		new Application().start();
	}
}
