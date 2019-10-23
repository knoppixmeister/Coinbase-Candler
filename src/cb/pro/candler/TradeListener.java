package cb.pro.candler;

public interface TradeListener {
	void onNewTrade(final Match trade, final String pair, final String rawData);
}
