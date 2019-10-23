package cb.pro.candler;

public interface OrderOpenedListener {
	public void onOrderOpened(final Match orderData, final String rawData);
}
