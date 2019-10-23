package cb.pro.candler;

public interface OrderReceivedListener {
	public void onOrderReceived(final Match orderData, final String rawData);
}
