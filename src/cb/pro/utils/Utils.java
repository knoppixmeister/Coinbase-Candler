package cb.pro.utils;

import java.math.*;
import java.nio.charset.*;
import java.util.UUID;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.apache.commons.codec.binary.*;

public class Utils {
	public static String encodeHMACSHA256(final String key, final String data) throws Exception {
		final Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
		sha256_HMAC.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));

		return Hex.encodeHexString(sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8)));
	}

	public static double round(double value, int places) {
		if(places < 0) throw new IllegalArgumentException();

		return (new BigDecimal(value)).setScale(places, RoundingMode.HALF_UP).doubleValue();
	}

	public static String signGenerate(final String secretKey, String requestPath, String method, String body, String timestamp) {
		try {
			final Mac sha256 = (Mac) Mac.getInstance("HmacSHA256").clone();
			sha256.init(new SecretKeySpec(
				java.util.Base64.getDecoder().decode(secretKey),
				Mac.getInstance("HmacSHA256").getAlgorithm()
			));

			return java.util.Base64.getEncoder().encodeToString(sha256.doFinal((timestamp + method.toUpperCase() + requestPath + body).getBytes()));
		}
		catch(Exception e) {
			e.printStackTrace();
		}

		return "";
	}

	public static boolean isUUID(String uuid) {
		if(uuid == null || uuid.isEmpty()) return false;

		try {
			UUID.fromString(uuid);
	        return true;
		}
		catch(Exception e) {
			return false;
		}
	}
}
