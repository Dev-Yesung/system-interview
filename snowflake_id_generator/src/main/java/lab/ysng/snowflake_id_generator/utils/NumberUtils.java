package lab.ysng.snowflake_id_generator.utils;

public final class NumberUtils {

	public static boolean isInRange(long number, long lowerBoundIncluded, long upperBoundIncluded) {
		return number >= lowerBoundIncluded && number <= upperBoundIncluded;
	}
}
