package utils;

public final class OrdinalUtils {

	private OrdinalUtils() {
	}

	public static String ordinal(int number) {
		int lastTwoDigits = Math.floorMod(number, 100);
		if (lastTwoDigits >= 10 && lastTwoDigits <= 20) {
			return number + "th";
		}

		return number + switch (Math.floorMod(number, 10)) {
			case 1 -> "st";
			case 2 -> "nd";
			case 3 -> "rd";
			default -> "th";
		};
	}
}