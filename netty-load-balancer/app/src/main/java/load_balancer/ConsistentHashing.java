package load_balancer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

public class ConsistentHashing {

	private static final String HASH_SUFFIX = "#";
	private static final String HASH_ALGORITHM = "SHA-1";
	private final SortedMap<Integer, String> hashRing = new TreeMap<>();
	private final int virtualNodeCount;

	public ConsistentHashing(List<String> servers, int virtualNodeCount) {
		this.virtualNodeCount = virtualNodeCount;
		for (String server : servers) {
			addServer(server);
		}
	}

	public void addServer(String server) {
		for (int i = 0; i < virtualNodeCount; i++) {
			int hash = hash(server + HASH_SUFFIX + i);
			hashRing.put(hash, server);
		}
	}

	public void removeServer(String server) {
		for (int i = 0; i < virtualNodeCount; i++) {
			int hash = hash(server + HASH_SUFFIX + i);
			hashRing.remove(hash);
		}
	}

	public Optional<String> getServer(String key) {
		if (hashRing.isEmpty()) {
			return Optional.empty();
		}

		int hash = hash(key);
		SortedMap<Integer, String> tailMap = hashRing.tailMap(hash);
		String server = !tailMap.isEmpty() ? hashRing.get(tailMap.firstKey()) : hashRing.get(hashRing.firstKey());

		return Optional.ofNullable(server);
	}

	private int hash(String key) {
		try {
			MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM);
			byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));

			// 상위 4바이트를 이용해 32비트 정수 생성
			return ((digest[0] & 0xFF) << 24) | (digest[1] & 0xFF) << 16
				   | ((digest[2] & 0xFF) << 8) | (digest[3] & 0xFF);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("SHA-1 Algorithm not found", e);
		}
	}
}
