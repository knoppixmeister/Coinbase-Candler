package cb.pro.rest;

import com.squareup.moshi.Moshi;

/*
	{
		"id":				"4c058fe8-da79-4afe-ad48-78a1b00feb85",
		"price":			"8000.00000000",
		"size":				"0.00125000",
		"product_id":		"BTC-EUR",
		"side":				"buy",
		"stp":				"dc",
		"type":				"limit",
		"time_in_force":	"GTC",
		"post_only":		true,
		"created_at":		"2019-07-19T13:51:55.702498Z",
		"fill_fees":		"0.0000000000000000",
		"filled_size":		"0.00000000",
		"executed_value":	"0.0000000000000000",
		"status":			"pending",
		"settled":			false
	}

	status = pending, rejected
*/
public class Order {
	public String id, price, size, product_id, side, type, time_in_force, created_at, fill_fees, filled_size, status, reject_reason;
	public boolean post_only;

	public String toString() {
		return new Moshi.Builder().build().adapter(Order.class).toJson(this);
	}
}
