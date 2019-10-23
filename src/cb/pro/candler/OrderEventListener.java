package cb.pro.candler;

public interface OrderEventListener {
	public void onOrderEvent(final String event, final Match orderData, final String rawData);
}
