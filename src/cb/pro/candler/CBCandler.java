package cb.pro.candler;

import java.math.*;
import java.util.*;
import java.util.concurrent.*;
import org.jfree.data.time.*;
import org.jfree.data.time.ohlc.*;
import org.joda.time.*;
import com.squareup.moshi.*;
import okhttp3.*;
import okhttp3.internal.ws.*;
import utils.*;

public class CBCandler {
	private static final String WS_URL 			=	"wss://ws-feed.pro.coinbase.com";
	private static final String SANDBOX_WS_URL	=	"wss://ws-feed-public.sandbox.pro.coinbase.com";

	private RealWebSocket webSocket = null;
	public static int OHLC_ITEMS_COUNT = 500;

	public boolean useSandbox = false;

	private final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder().retryOnConnectionFailure(true)
																		.connectTimeout(10, TimeUnit.SECONDS)
																		.pingInterval(10, TimeUnit.SECONDS)
																		.build();
	private final Moshi MOSHI											=	new Moshi.Builder().build();
	public final Map<String, Map<Integer, OHLCSeries>> OHLC_SERIES		=	new ConcurrentHashMap<>();

	private final List<CandleListener> candleListeners					=	new CopyOnWriteArrayList<>();
	private final List<TradeListener> tradeListeners					=	new CopyOnWriteArrayList<>();
	private final List<OrderReceivedListener> orderReceivedListeners	=	new CopyOnWriteArrayList<>();
	private final List<OrderDoneListener> orderDoneListeners			=	new CopyOnWriteArrayList<>();
	private final List<OrderOpenedListener> orderOpenedListeners		=	new CopyOnWriteArrayList<>();

	private final List<OrderEventListener> orderEventListeners			=	new CopyOnWriteArrayList<>();

	private final List<OrderBookListener> orderBookListeners			=	new CopyOnWriteArrayList<>();

	private final List<ConnectedListener> connectedListeners			=	new CopyOnWriteArrayList<>();

	private final List<SocketMessageListener> socketMessageListeners	=	new CopyOnWriteArrayList<>();

	private final JsonAdapter<Match> matchJsonAdapter					=	MOSHI.adapter(Match.class);
	private Match m;

	private final JsonAdapter<Snapshot> snapshotJsonAdapter				=	MOSHI.adapter(Snapshot.class);
	private final JsonAdapter<L2Update> l2UpdateJsonAdapter				=	MOSHI.adapter(L2Update.class);

	private Snapshot snapshot;
	private L2Update l2Update;

	private final ConcurrentSkipListMap<Double, Double> OB_UP_BUY		=	new ConcurrentSkipListMap<Double, Double>();
	private final ConcurrentSkipListMap<Double, Double> OB_DOWN_SELL	=	new ConcurrentSkipListMap<Double, Double>();

	private int reconnectCnt = -1;

	private boolean allowReconnect = true;

	public boolean add(String pair, int interval) {
		if(!OHLC_SERIES.containsKey(pair.toUpperCase())) {
			ConcurrentHashMap<Integer, OHLCSeries> ohlcs = new ConcurrentHashMap<>();
			OHLC_SERIES.put(pair.toUpperCase(), ohlcs);
		}
		if(!OHLC_SERIES.get(pair.toUpperCase()).containsKey(interval)) {
			OHLC_SERIES.get(pair.toUpperCase()).put(interval, new OHLCSeries(""));
		}

		if(!fetchOHLCs(pair, interval)) {
			System.out.println("COULD NOT RECEIVE INITIAL CANDLES. EXIT !!!");
			System.exit(0);
		}

		if(webSocket != null) {
			webSocket.send(
				"{\"type\":\"subscribe\",\"channels\":["+
				"	{\"name\":\"full\",\"product_ids\":[\""+pair+"\"]},"+	// BTC-EUR
				"	{\"name\":\"level2\",\"product_ids\":[\""+pair+"\"]}"+
				"]}"
			);
		}

		return true;
	}

	@SuppressWarnings({ "unchecked", "unused" })
	private boolean fetchOHLCs(final String pair, final int interval) {
		System.out.print("CB_CANDLER. START FETCHING INITIAL CANDLES "+pair+"/"+interval+" .... ");

		OHLC_SERIES.get(pair.toUpperCase()).get(interval).clear();

		final String ohlcsUrl = "https://api.cryptowat.ch/markets/coinbase-pro/"+pair.replaceAll("-", "").toLowerCase()+"/ohlc?periods="+interval;

		final Request request = new Request.Builder().url(ohlcsUrl).build();
		try {
			final Response response = HTTP_CLIENT.newCall(request).execute();
			if(response.isSuccessful()) {
				String json = response.body().string();

				json = json.substring(json.indexOf("["));
				json = json.substring(0, json.indexOf("]]")+2);

				final List<List<Object>> restKlines = new Moshi.Builder().build().adapter(List.class).fromJson(json);

				DateTime dtEnd, dtStart;
				List<Object> rlRes;

				OHLC_SERIES.get(pair.toUpperCase()).get(interval).setNotify(false);

				for(int key=restKlines.size()-1; key >= 0; key--) {
					rlRes 	= restKlines.get(key);
					dtEnd 	= new DateTime(new BigDecimal(rlRes.get(0)+"").longValue()*1000);
					dtStart = new DateTime((new BigDecimal(rlRes.get(0)+"").longValue()-interval)*1000);

					try {
						OHLC_SERIES.get(pair.toUpperCase()).get(interval).add(
							new FixedMillisecond((new BigDecimal(rlRes.get(0)+"").longValue()-interval)*1000),
							new BigDecimal(rlRes.get(1)+"").doubleValue(),
							new BigDecimal(rlRes.get(2)+"").doubleValue(),
							new BigDecimal(rlRes.get(3)+"").doubleValue(),
							new BigDecimal(rlRes.get(4)+"").doubleValue(),
							new BigDecimal(rlRes.get(5)+"").doubleValue()
						);
					}
					catch(Exception e1) {
						e1.printStackTrace();
					}
				}

				OHLC_SERIES.get(pair.toUpperCase()).get(interval).setNotify(true);

				System.out.println("DONE");

				return true;
			}
			else {
				System.err.println("ERROR");
				System.err.println(response.body().string());
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}

		return false;
	}

	public int getReconnectCnt() {
		return reconnectCnt;
	}

	public void connect() {
		Log.i("CB_CANDLER. CONNECTING ... ");

		reconnectCnt += 1;

		if(webSocket != null) {
			allowReconnect = false;
			webSocket.close(1000, "CLOSE WS BEFORE CONNECT IF ALREADY CREATED CONNECTION");
			webSocket = null;
		}

		if(reconnectCnt > 0) {
			boolean allPairCandlesReceived = true;
			while(true) {
				allPairCandlesReceived = true;

				for(String pair : OHLC_SERIES.keySet()) {
					for(Integer interval : OHLC_SERIES.get(pair.toUpperCase()).keySet()) {
						OHLC_SERIES.get(pair.toUpperCase()).get(interval).clear();

						if(!fetchOHLCs(pair, interval)) allPairCandlesReceived = false;
					}
				}

				if(!allPairCandlesReceived) {
					try {
						System.out.println("NOT ALL INITIAL CANDLE PAIRS RECEIVED AFTER RECONNECT ATTMPT. TRY AGAIN AFTER 5 sec.");

						Thread.sleep(5 * 1000);
					}
					catch(Exception e) {
						e.printStackTrace();
					}
				}
				else break;
			}
		}

		allowReconnect = true;

		webSocket = (RealWebSocket) HTTP_CLIENT.newWebSocket(
			new Request.Builder().url(useSandbox ? SANDBOX_WS_URL : WS_URL).build(),
			new WebSocketListener() {
				public void onOpen(final WebSocket socket, final Response response) {
					Log.i("CB_CANDLER. CONNECTING DONE");

					try {
						Log.i("CB_WS_ON_OPEN"+(response != null ? ": "+response.body().string() : ""));
					}
					catch(Exception e) {
						e.printStackTrace();
					}

					for(String pair : OHLC_SERIES.keySet()) {
						socket.send(
							"{\"type\":\"subscribe\",\"channels\":["+
							"	{\"name\":\"full\",\"product_ids\":[\""+pair+"\"]},"+	// BTC-EUR
							"	{\"name\":\"level2\",\"product_ids\":[\""+pair+"\"]},"+
							"	{\"name\":\"status\"}"+
							"]}"
						);
					}

					for(ConnectedListener cl : connectedListeners) {
						cl.onConnectedListener();
					}
				}

				public void onClosed(final WebSocket socket, final int code, final String reason) {
					Log.i("CB_WS_CLOSED. CODE: "+code+"; REASON: '"+reason+"'");

					if(allowReconnect) connect();
				}

				public void onFailure(final WebSocket socket, final Throwable t, final Response response) {
					t.printStackTrace();

					if(response != null) {
						try {
							System.out.println(response.body().string());
						}
						catch(Exception e) {
							e.printStackTrace();
						}
					}

					if(allowReconnect) connect();
				}

				public void onMessage(final WebSocket socket, final String msg) {
					for(SocketMessageListener socketMessageListener : socketMessageListeners) {
						socketMessageListener.onWebSocketMessage(msg);
					}

					parseMessage(msg);
				}
			}
		);
	}

	public void addConnectedListener(ConnectedListener connectedListener) {
		connectedListeners.add(connectedListener);
	}

	public void addCandleListener(CandleListener listener) {
		candleListeners.add(listener);
	}

	public void addTradeListener(TradeListener tradeListener) {
		tradeListeners.add(tradeListener);
	}

	public void addOrderReceivedListener(OrderReceivedListener orderReceivedListener) {
		orderReceivedListeners.add(orderReceivedListener);
	}

	public void addOrderDoneListener(OrderDoneListener orderDoneListener) {
		orderDoneListeners.add(orderDoneListener);
	}

	public void addOrderOpenedListener(OrderOpenedListener orderOpenedListener) {
		orderOpenedListeners.add(orderOpenedListener);
	}

	public void addOrderEventListener(OrderEventListener orderEventListener) {
		orderEventListeners.add(orderEventListener);
	}

	public void addOrderBookListener(OrderBookListener orderBookListener) {
		orderBookListeners.add(orderBookListener);
	}

	public void addSocketMessageListener(SocketMessageListener socketMessageListener) {
		socketMessageListeners.add(socketMessageListener);
	}

	private void parseMessage(String message) {
		// Log.i("ON_MSG: "+message);

		if(message.contains("\"type\":\"l2update\"")) {
			// Log.i("ON_MSG: "+message);

			try {
				l2Update = l2UpdateJsonAdapter.fromJson(message);

				for(List<String> entry : l2Update.changes) {
					if(entry.get(0).equals("sell")) {
						if(Double.valueOf(entry.get(2))	== 0) {
							OB_UP_BUY.remove(Double.valueOf(entry.get(1)));
						}
						else OB_UP_BUY.put(Double.valueOf(entry.get(1)), Double.valueOf(entry.get(2)));
					}
					else {
						if(Double.valueOf(entry.get(2))	== 0) {
							OB_DOWN_SELL.remove(Double.valueOf(entry.get(1)));
						}
						else OB_DOWN_SELL.put(Double.valueOf(entry.get(1)), Double.valueOf(entry.get(2)));
					}
				}

				/*
				System.out.println(
					"ASK: "+OB_UP_BUY.get(OB_UP_BUY.firstKey())	+" "+OB_UP_BUY.firstKey()+"\r\n"+
					"----------------------------------\r\n"+
					"BID: "+OB_DOWN_SELL.get(OB_DOWN_SELL.lastKey())+" "+OB_DOWN_SELL.lastKey()+"\r\n"+
					"----------------------------------------------------------------------------------------------------------------------"
				);
				System.out.println();
				*/

				for(OrderBookListener obl : orderBookListeners) {
					obl.onNewOrderBook(l2Update.product_id,	OB_UP_BUY, OB_DOWN_SELL);
				}
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}

		if(message.contains("snapshot")) {
			// Log.i("ON_MSG_SNAPSH: "+message);

			try {
				snapshot = snapshotJsonAdapter.fromJson(message);

				for(List<String> entry : snapshot.asks) {
					OB_UP_BUY.put(
						Double.valueOf(entry.get(0)),
						Double.valueOf(entry.get(1))
					);
				}
				for(List<String> entry : snapshot.bids) {
					OB_DOWN_SELL.put(
						Double.valueOf(entry.get(0)),
						Double.valueOf(entry.get(1))
					);
				}

				/*
				System.out.println(
					"\r\n"+
					"ASK: "+snapshot.asks.get(0)+"\r\n"+
					"----------------------------------\r\n"+
					"BID: "+snapshot.bids.get(0)+
					"\r\n"
				);
				*/
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}

		if(message.contains("\"type\":\"status\"")) {
			// Log.i("ON_MSG_STATUS: "+message+"\r\n");
		}

		if(message.contains("\"type\":\"open\"")) {
			// System.out.println("\r\nON_MSG_OPEN: "+message+"\r\n");

			try {
				m = matchJsonAdapter.fromJson(message);

				for(OrderOpenedListener ool : orderOpenedListeners) {
					ool.onOrderOpened(m, message);
				}

				for(OrderEventListener oel : orderEventListeners) {
					oel.onOrderEvent("open", m, message);
				}
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}

		if(message.contains("\"type\":\"done\"")) {
			// System.out.println("\r\nON_MSG_DONE: "+message+"\r\n");

			try {
				m = matchJsonAdapter.fromJson(message);

				for(OrderDoneListener odl : orderDoneListeners) {
					odl.onOrderDone(m, message);
				}

				for(OrderEventListener oel : orderEventListeners) {
					oel.onOrderEvent("done", m, message);
				}
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}

		/*
			{
				"type":			"received",
				"order_id":		"809da0c5-da95-4e4....",
				"order_type":	"limit",
				"size":			"0.18000000",
				"price":		"9372.91000000",
				"side":			"buy",
				"client_oid":	"",
				"product_id":	"BTC-EUR",
				"sequence":		564410,
				"time":			"2019-07-19T12:19:09.348000Z"
			}
		*/
		if(message.contains("\"type\":\"received\"")) {
			// System.out.println("\r\nON_MSG_REC: "+message+"\r\n");

			try {
				m = matchJsonAdapter.fromJson(message);

				for(OrderReceivedListener orl : orderReceivedListeners) {
					orl.onOrderReceived(m, message);
				}

				for(OrderEventListener oel : orderEventListeners) {
					oel.onOrderEvent("received", m, message);
				}
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}

		if(message.contains("\"type\":\"match\"")) {
			// Log.i("\r\nON_MSG: "+message+"\r\n");

			DateTime tradeDT;
			try {
				m = matchJsonAdapter.fromJson(message);

				for(TradeListener tl : tradeListeners) {
					tl.onNewTrade(m, m.product_id, message);
				}

				for(OrderEventListener oel : orderEventListeners) {
					oel.onOrderEvent("match", m, message);
				}

				tradeDT = new DateTime(m.time);

				OHLCItem lastCandle;
				long lastCandleEndMs;

				if(OHLC_SERIES != null && OHLC_SERIES.containsKey(m.product_id)) {
					boolean isCandleUpdate = true;

					for(Integer intervalKey : OHLC_SERIES.get(m.product_id).keySet()) {
						if(OHLC_SERIES.get(m.product_id).get(intervalKey).getItemCount() < 1) continue;

						lastCandle		=	(OHLCItem) OHLC_SERIES.get(m.product_id).get(intervalKey).getDataItem(OHLC_SERIES.get(m.product_id).get(intervalKey).getItemCount()-1);
						lastCandleEndMs	=	lastCandle.getPeriod().getFirstMillisecond()+(intervalKey*1000);

						if(tradeDT.getMillis() > lastCandleEndMs) {
							OHLC_SERIES.get(m.product_id).get(intervalKey).add(
								new FixedMillisecond(lastCandleEndMs),
								Double.parseDouble(m.price),
								Double.parseDouble(m.price),
								Double.parseDouble(m.price),
								Double.parseDouble(m.price),
								Double.parseDouble(m.size)
							);

							if(OHLC_SERIES.get(m.product_id).get(intervalKey).getItemCount() > OHLC_ITEMS_COUNT) {
								OHLC_SERIES.get(m.product_id).get(intervalKey).remove(0);
							}

							isCandleUpdate = false;
						}
						else {// update last candle
							OHLC_SERIES.get(m.product_id).get(intervalKey).updatePriceVolume(Double.parseDouble(m.price), lastCandle.getVolume()+Double.parseDouble(m.size));

							isCandleUpdate = true;
						}

						for(CandleListener cl : candleListeners) {
							cl.onNewCandleData(
								OHLC_SERIES.get(m.product_id).get(intervalKey),
								m.product_id.toUpperCase(),
								intervalKey,
								isCandleUpdate,
								message
							);
						}
					}
				}
			}
			catch(Exception e) {
				e.printStackTrace();
			}

			//System.out.println("-------------------------------------------------------------------------------------------------------------");
		}
	}
}
