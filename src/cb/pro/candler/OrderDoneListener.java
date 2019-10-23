package cb.pro.candler;

public interface OrderDoneListener {
	public void onOrderDone(final Match orderData, final String rawData);
}
