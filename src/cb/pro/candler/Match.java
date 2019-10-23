package cb.pro.candler;

import com.squareup.moshi.*;

public class Match {
	public String type, order_id, side, size, price, time, product_id, taker_order_id, maker_order_id, client_oid, reason, remaining_size, order_type;
	public long sequence, trade_id;

	public String getTime() {
		String tm = time.replaceAll("T", " ");
		tm = tm.substring(0, tm.lastIndexOf("."));

		return tm;
	}

	@Override
	public String toString() {
		//return product_id+" | SD: "+side+" | SZ: "+size+" | PR: "+price+" | TM: "+getTime()+" | RM_SZ: "+remaining_size;

		return new Moshi.Builder().build().adapter(Match.class).toJson(this);
	}
}
