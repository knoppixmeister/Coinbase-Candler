package cb.pro.rest;

import com.squareup.moshi.*;

public class Account {
	public String id, currency, balance, available, hold, profile_id;

	@Override
	public String toString() {
		return new Moshi.Builder().build().adapter(Account.class).toJson(this);
	}
}
