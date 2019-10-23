package cb.pro.rest;

import com.squareup.moshi.*;

public class Fill {
	/*
		"created_at":	"2019-04-12T20:37:29.783Z",
		"trade_id":		18664599,
		"product_id":	"BTC-EUR",
		"order_id":		"2c487a65-d7ca-45c1-9a37-fba324eecdb9",
		"user_id":		"5a6a0096ddb5e70163230e46",
		"profile_id":	"57f91185-e5c3-40a6-be86-b9a9603361cc",
		"liquidity":	"M",
		"price":		"4460.00000000",
		"size":			"0.00300000",
		"fee":			"0.0200700000000000",
		"side":			"buy",
		"settled":		true,
		"usd_volume":	"15.0818700000000000"
	*/

	public String created_at, product_id, liquidity, price, fee, side, size, order_id;
	public long trade_id;
	public boolean settled;

	public String toString() {
		return new Moshi.Builder().build().adapter(Fill.class).toJson(this);
	}
}
