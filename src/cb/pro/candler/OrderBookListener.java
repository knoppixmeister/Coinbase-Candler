package cb.pro.candler;

import java.util.concurrent.ConcurrentSkipListMap;

public interface OrderBookListener {
	public void onNewOrderBook(String productId, ConcurrentSkipListMap<Double, Double> OB_UP_BUY, ConcurrentSkipListMap<Double, Double> OB_DOWN_SELL);
}
