package dk.alexandra.organicity.orion;


import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.log4j.Logger;
import org.json.JSONObject;

import com.amaxilatis.orion.OrionClient;
import com.amaxilatis.orion.model.subscribe.OrionEntity;
import com.amaxilatis.orion.model.subscribe.SubscriptionResponse;

import dk.alexandra.orion.websocket.transports.OrionSubscription;



/**
 * 
 * @author Morten Skov
 *
 * Responsible for handling the connections specific to the Orion Context Broker
 * Settings for the broker, can be set in conncetion.properties
 *
 */
public class Connector {
	
	protected static final Logger LOGGER = Logger.getLogger(Connector.class);
	
    private OrionClient client;
    private SimpleDateFormat df;
    private Properties properties;
    private String localURI;
    private HashMap<String, OrionSubscription> subscriptions = new HashMap<>();
    private HashMap<String, ArrayList<String>> clientIndexedSubscriptions = new HashMap<>();
    private String serverUrl;
    private String tokenUrl = null;
    private String token = null;
    private String wsClientId;
    private String wsClientSecret;
    private int delay = 0;

    
    
    public static void main(String[] args){
    	Connector c = new Connector();
    	String[] attr = new String[1];
    	attr[0] = "temperature";
    	String[] cond = new String[1];
    	cond[0] = "pressure";
		//String entityId = "urn:oc:entity:experimenters:cf2c1723-3369-4123-8b32-49abe71c0e57:5846db253be86fb0409329e8:11";
    	String entityId = "urn:oc:entity:experimenters:5a660d96-0ef7-42ca-9f6c-5dbb86d6aa20:58ab32f36f8b513746565c54:wsasset";
    	OrionSubscription subscription = new OrionSubscription(cond, attr, "P1D", entityId, false, "Room",null,null);
    	
    	
    	String[] res = c.registerSubscription(subscription, "XXX","XXX");
    	System.out.println(res);
    }
    
    /**
	 * Initiates the connection to the Context Broker
	 * 
	 */
	public Connector(){
        TimeZone tz = TimeZone.getTimeZone("UTC");
        df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
        df.setTimeZone(tz);
        
        serverUrl = "http://192.168.121.132:1026";
        localURI = "http://192.168.121.1:8090/receiveNotifications";
        
        try{
        	properties = new Properties();
            properties.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("connection.properties"));
            serverUrl = properties.getProperty("serverUrl");
            
            localURI = properties.getProperty("localURI");
            tokenUrl = properties.getProperty("tokenUrl");
            wsClientId = properties.getProperty("clientId");
            wsClientSecret = properties.getProperty("clientSecret");
        }catch(IOException e){
        	e.printStackTrace();
        	LOGGER.error("not able to use properties. Continuing with default values");
        	System.exit(1);
        }
        
        
        token = getClientCredentialGrantToken(tokenUrl, wsClientId, wsClientSecret);
        /*
        try {
        	JwtParser fwtparser = new JwtParser();
        	System.out.println(token);
        	token = "eyJhbGciOiJSUzI1NiJ9.eyJqdGkiOiI5MWEzY2Q3YS0zOTAxLTRkOTMtYjIzMi1iMThjYzY3ZmMwYzEiLCJleHAiOjE0OTA2NDE3NjgsIm5iZiI6MCwiaWF0IjoxNDkwNjQxNDY4LCJpc3MiOiJodHRwczovL2FjY291bnRzLm9yZ2FuaWNpdHkuZXUvcmVhbG1zL29yZ2FuaWNpdHkiLCJhdWQiOiI0ZmFmNmQxMi05ZDdlLTQ5OTktOTA3MC05ZWYzNmJiNDU4NDEiLCJzdWIiOiIwYzhkNzE3NC01OWU4LTQyNDYtODgxNi0zMDYyNWY1ZjdmMzQiLCJ0eXAiOiJCZWFyZXIiLCJhenAiOiI0ZmFmNmQxMi05ZDdlLTQ5OTktOTA3MC05ZWYzNmJiNDU4NDEiLCJzZXNzaW9uX3N0YXRlIjoiZGIxZTk4NDAtOGM0MC00Zjc1LWI3Y2MtNTgxYThhN2U5OWZkIiwiY2xpZW50X3Nlc3Npb24iOiIyNjliZTEzNi01MWZkLTQ2MDMtYjVhZS04ZGRiZjg3MDQyY2QiLCJhbGxvd2VkLW9yaWdpbnMiOltdLCJyZWFsbV9hY2Nlc3MiOnsicm9sZXMiOlsiZXhwZXJpbWVudGVyIl19LCJyZXNvdXJjZV9hY2Nlc3MiOnsiZGVtby1vY3NpdGUiOnsicm9sZXMiOlsiZGVsZXRlLWFzc2V0IiwicmVhZC1hc3NldCIsImNyZWF0ZS1hc3NldCIsInVwZGF0ZS1hc3NldCJdfSwic2l0ZS1tYW5hZ2VyIjp7InJvbGVzIjpbImRpY3Rpb25hcnktdXNlciJdfSwic2l0ZS1tYW5hZ2VyLWRldiI6eyJyb2xlcyI6WyJkaWN0aW9uYXJ5LXVzZXIiXX19LCJjbGllbnRJZCI6IjRmYWY2ZDEyLTlkN2UtNDk5OS05MDcwLTllZjM2YmI0NTg0MSIsImNsaWVudEhvc3QiOiIxODguMTc3LjI3LjEyNCIsIm5hbWUiOiIiLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJzZXJ2aWNlLWFjY291bnQtNGZhZjZkMTItOWQ3ZS00OTk5LTkwNzAtOWVmMzZiYjQ1ODQxIiwiY2xpZW50QWRkcmVzcyI6IjE4OC4xNzcuMjcuMTI0IiwiZW1haWwiOiJzZXJ2aWNlLWFjY291bnQtNGZhZjZkMTItOWQ3ZS00OTk5LTkwNzAtOWVmMzZiYjQ1ODQxQHBsYWNlaG9sZGVyLm9yZyJ9.gAtvctddGtEFU7yHmmu3qbEab3VOmfv29XNlBUt2TxeFIRuO8voqAQSrLS96qF8e5nZbBsYUYMv30FWrH2_5Ou0qVc8dbgyb1D08Fa_KtdbiyxjKxiPt5gGqsJnjmBvAKoQZxeJ6_MsUB0IAjr_LdyVAVHqRtWCfRH5o_pPo-aBHzULfdrjRR_Q60DhiihAoJKCzXzEXNDmEYQoxTBbBq_Rxrmz7dnNaWs5oCzYf21Fr_cwSvAM1YFOqjcsemVXYk1N3I_vtYDyKNVRZigNl4NHmw8yTs_e6JSPkf5tZ1PgKZ5dpDriEAfXflt8Ar4Pe8nCGFGpJ2pQjkV2s2lFqWw";
        	Claims claim = fwtparser.parseJWT(token);
        	LOGGER.info("WTF");
        	System.out.println(claim.get("clientId"));
        	// Token valid
        } catch (Exception e) {
        	e.printStackTrace();
        	// Token invalid
        }
        */
        //LOGGER.info(token);
        //LOGGER.info("Connecting to url: "+serverUrl);
        client = new OrionClient(serverUrl,token, "organicity", "/");
        
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        //continously update token when it expires
        Runnable task = () -> {
            token = getClientCredentialGrantToken(tokenUrl, wsClientId, wsClientSecret);
            client = new OrionClient(serverUrl,token, "organicity", "/");
            LOGGER.error("Updated token from OAuth2 server");
        };

        executor.scheduleWithFixedDelay(task, delay, delay, TimeUnit.SECONDS);

	}
	
	private String getClientCredentialGrantToken(String tokenUrl, String clientId, String clientSecret) {
		System.out.println("getting new token");
		SSLContext sc = null;
		try{
			sc = SSLContext.getInstance("SSL"); 
		    sc.init(null, getTrustManager(), new java.security.SecureRandom());
		}catch(NoSuchAlgorithmException|KeyManagementException e){
			LOGGER.error("Exception thrown while setting up certificats: \n"+e);
			return null;
		}
		
		
		Client c = ClientBuilder.newBuilder().sslContext(sc).build();
		
		String path = "/realms/organicity/protocol/openid-connect/token";
		WebTarget webTarget = c.target(tokenUrl).path(path);
		LOGGER.info("Connecting to token Url: "+tokenUrl+path);
		String base64 = Base64.getEncoder().encodeToString((clientId+":"+clientSecret).getBytes());
		
		
		Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON_TYPE).header("Authorization","Basic "+base64);
		

		Form form = new Form();		
		form.param("grant_type", "client_credentials");
		
		Entity entity =  Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE);
		Response response = invocationBuilder.post(entity);
		
		JSONObject tokenEntity = new JSONObject(response.readEntity(String.class));
		
		try{
			String expires = tokenEntity.get("expires_in").toString();
			delay = Integer.parseInt(expires);
		}catch(NumberFormatException e){
			delay = 200;
		};
		
		return tokenEntity.get("access_token").toString();
	}
	
	
	/**
	 * Registering a subscription at the Context Broker
	 * 
	 * @param subscription A POJO containing the subscription data needed to set a subscription
	 * @param sessionId The id of the client requesting the subscription
	 * 
	 * @return The subscriptionId if successful, null otherwise
	 */
	public String[] registerSubscription(OrionSubscription subscription, String sessionId, String clientId){
		String subscriptionId = null;
		String[] methodResponse = new String[2];
		methodResponse[0] = "error";
		//using clean java http client, as OrionClient is non functioning with simple get
		//Client c = ClientBuilder.newClient( new ClientConfig().register( LoggingFilter.class ) );
		SSLContext sc = null;
		try{
			sc = SSLContext.getInstance("SSL"); 
		    sc.init(null, getTrustManager(), new java.security.SecureRandom());
		}catch(NoSuchAlgorithmException|KeyManagementException e){
			LOGGER.error("Exception thrown while setting up certificats: \n"+e);
			return null;
		}
		
		
		Client c = ClientBuilder.newBuilder().sslContext(sc).build();
		WebTarget webTarget = c.target(serverUrl).path("/v2/entities/"+subscription.getId());
		System.out.println(webTarget);
		Invocation.Builder invocationBuilder =  webTarget.request(MediaType.APPLICATION_JSON).header("Fiware-Service", " organicity");
		Response checkResponse = invocationBuilder.get();
		
		JSONObject checkEntity = new JSONObject(checkResponse.readEntity(String.class));
		System.out.println(checkEntity);
		if(checkEntity.has("error")){
			//entity does not exist
			subscriptionId = "Sorry, entity not available"; 
			LOGGER.info("Client tried to access unknown entity: "+subscription.getId());
		}else if(checkEntity.has("access:scope") && ((JSONObject)(checkEntity.get("access:scope"))).has("value") && ((JSONObject)(checkEntity.get("access:scope"))).get("value").equals("private") && !subscription.getId().contains(clientId)){
			subscriptionId = "Sorry, entity not available";
			LOGGER.info("Client tried to access private entity: "+subscription.getId());
		}
		
		
		if(subscriptionId!=null){
			methodResponse[1] = subscriptionId;
			return methodResponse;
		}
			
		
		
		OrionEntity entity = new OrionEntity();
		entity.setId(subscription.getId());
		entity.setIsPattern(String.valueOf(subscription.isPattern()));
		entity.setType(subscription.getType());
		String[] attributes = subscription.getAttributes();
		String[] conditions = subscription.getConditions();
		String duration = subscription.getDuration();
		try{
			subscriptionId = "Not able to subscribe at the moment. Please try again";
			
			SubscriptionResponse response = client.subscribeChange(entity, attributes, localURI,conditions, duration);
			if(response!=null){
				subscriptionId = response.getSubscribeResponse().getSubscriptionId();
				subscription.setSubscriberId(sessionId);
				subscriptions.put(subscriptionId, subscription);
				if(clientIndexedSubscriptions.get(sessionId)==null){
					clientIndexedSubscriptions.put(sessionId, new ArrayList<String>(Arrays.asList(subscriptionId)));
				}else{
					List<String> subscriptions = clientIndexedSubscriptions.get(sessionId);
					if(!subscriptions.contains(subscriptions)){
						subscriptions.add(subscriptionId);
					}
				}
				methodResponse[0] = "subscriptionId";
			}
			
		}catch(IOException e){
			LOGGER.error("Not able to add subscription: "+e.getStackTrace());
			subscriptionId = "Something went wrong when trying to subscribe. Please try again";
		}
		methodResponse[1] = subscriptionId;
		return methodResponse;
	}
	
	
	/**
	 * Removing a subscription from Context Broker
	 * 
	 * @param subscriptionId The specific subscriptionId of the subscription wished to be removed
	 * @param clientId The id of the client requesting the subscription
	 * 
	 * @return subscriptionId if remove is successful, null otherwise
	 */
	public String removeSubscription(String subscriptionId, String clientId){
		try {
			LOGGER.info("Sending request to remove subscription with id: "+subscriptionId);
			SubscriptionResponse response = client.unSubscribeChange(subscriptionId);
			if(response.getSubscribeError()==null){
				subscriptions.remove(subscriptionId);
				List<String> clientSubscriptions = clientIndexedSubscriptions.get(clientId);
				if(clientSubscriptions!=null){
					clientSubscriptions.remove(subscriptionId);
				}
				return subscriptionId;
			}else{
				LOGGER.error("Error while unscribing subscription with id: "+subscriptionId+": "+response.getSubscribeError());
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			LOGGER.error("Error while unscribing subscription with id: "+subscriptionId+": "+e.getStackTrace());
			//e.printStackTrace();
			
		}
		return null;
	}
	
	
	/**
	 * Method for getting a clientId from a given subscriptionId
	 * 
	 * @param subscriptionId The specific subscriptionId
	 * 
	 * @return The clientId from that subscription
	 */
	public String getSubscriptionSessionId(String subscriptionId){
		String sessionId = subscriptions.get(subscriptionId).getSubscriberId();
		return sessionId;
	}
	
	
	/**
	 * Get all subscriptions
	 * 
	 * @return The list of subscriptions
	 */
	public HashMap<String, OrionSubscription> getSubscriptions(){
		return subscriptions;
	}
	
	
	/**
	 * Method for handling cleaning up after a client disconnects
	 * 
	 * @param clientId The id of the client requesting the subscription
	 * 
	 * @return A boolean if clean up succeded
	 */
	public boolean clientDisconnected(String clientId){
		ArrayList<String> clientSubscriptions = clientIndexedSubscriptions.get(clientId);
		if(clientSubscriptions==null){
			//no subscriptions found. all good
			return true;
		}
		
		List<String> subscriptions = (ArrayList)clientSubscriptions.clone();
		
		boolean allGood = true;
		
		if(subscriptions!=null){
			for(String subscriptionId: subscriptions){
				String res = removeSubscription(subscriptionId, clientId);
				if(res==null){
					allGood=false;
				}
			}
		}
		clientIndexedSubscriptions.remove(clientId);
		
		return allGood;
	}
	
	/**
	 * Method for getting a trust manager for handling the SSL connections
	 * This is a VERY bad solution as it accepts all certificates. But it is needed as OC atm runs with self signed certs...
	 * 
	 * 
	 * @return A TrustManager array
	 */
	
	private TrustManager[] getTrustManager(){
		// Create a trust manager that does not validate certificate chains
		TrustManager[] trustAllCerts = new TrustManager[] { 
				new X509TrustManager() {     
					public java.security.cert.X509Certificate[] getAcceptedIssuers() { 
						return new X509Certificate[0];
					} 
					public void checkClientTrusted( 
							java.security.cert.X509Certificate[] certs, String authType) {
					} 
					public void checkServerTrusted( 
							java.security.cert.X509Certificate[] certs, String authType) {
					}
				} 
		};
		return trustAllCerts;	
	}



}
