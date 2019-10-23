package cb.pro.candler;

import java.util.List;
import com.squareup.moshi.Moshi;

public class L2Update {
	public String type, product_id, time;
	public List<List<String>> changes;

	public String toString() {
		return new Moshi.Builder().build().adapter(L2Update.class).toJson(this);
	}
}
