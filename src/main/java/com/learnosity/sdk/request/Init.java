package com.learnosity.sdk.request;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import org.apache.commons.codec.digest.DigestUtils;
import org.json.JSONObject;
import org.json.JSONArray;

import java.util.ArrayList;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.learnosity.sdk.request.Init.Service.Assess;
import static com.learnosity.sdk.request.Init.Service.Data;
import static com.learnosity.sdk.request.Init.Service.Questions;


/**
 *--------------------------------------------------------------------------
 * Learnosity SDK - Init
 *--------------------------------------------------------------------------
 *
 * Used to generate the necessary security and request data (in the
 * correct format) to integrate with any of the Learnosity API services.
 *
 */

public class Init {
    /**
     * Which Learnosity service to generate a request packet for.
     * Valid values (see also `$validServices`):
     *  - assess
     *  - author
     *  - data
     *  - items
     *  - questions
     *  - reports
     */
    private Service service;
    
    /**
     * The consumer secret as provided by Learnosity. This is your private key
     * known only by the client (you) and Learnosity, which must not be exposed
     * either by sending it to the browser or across the network.
     * It should never be distributed publicly.
     */
    private String secret;
    
    /**
     * A JSONObject of security details. This typically contains:
     *  - consumer_key
     *  - domain (optional depending on which service is being initialised)
     *  - timestamp (optional)
     *  - user_id (optional depending on which service is being initialised)
     *
     * It's important that the consumer secret is NOT a part of this array.
     */
    private JSONObject securityPacket;

    /**
     * An optional JSONObject of request parameters used as part
     * of the service (API) initialisation.
     */
    private JSONObject requestPacket;

    /**
     * If `requestPacket` is used, `requestString` will be the string
     * (JSON) representation of that. It's used to create the signature
     * and returned as part of the service initialisation data.
     */
    private String requestString = "";

    /**
     * An optional value used to define what type of request is being
     * made. This is only required for certain requests made to the
     * Data API (http://docs.learnosity.com/dataapi/)
     */
    private String action = "";

    /**
     * Most services add the request packet (if passed) to the signature
     * for security reasons. This flag can override that behaviour for
     * services that don't require this.
     */
    private boolean signRequestData = true;

    /**
     * Key names that are valid in the securityPacket, they are also in
     * the correct order for signature generation. 
     */
    public static final List<String> VALID_SECURITY_KEYS = ImmutableList.of("consumer_key", "domain", "timestamp", "user_id");

    /**
     * Valid strings for service
     */
    public enum Service {
		Assess("assess"), Author("author"), Data("data"), Items("items"), Questions("questions"), Reports("reports"), Events("events");
		private final String key;

		private Service(String key) {
			this.key = key;
		}
		public String getKey() {
			return this.key;
		}
	}
    
    /**
     * Instantiate this class with all security and request data. It
     * will be used to create a signature.
     *
     * @param service        the service to be used
     * @param securityPacket any object which can be used to instantiate a json.org.JSONObject or a json.org.JSONObject
     * @param secret         the private key
     */
    public Init (Service service, Object securityPacket, String secret)
    {
        // First validate and set the arguments
        this.validateRequiredArgs(service, securityPacket, secret);

        // Set any service specific options
        this.setServiceOptions();

        // Generate the signature based on the arguments provided
        this.securityPacket.put("signature", this.generateSignature());
    }
       
    
    /**
     * Instantiate this class with all security and request data. It
     * will be used to create a signature.
     *
     * @param service        the service to be used
     * @param securityPacket the security information. Can be a json.org.JSONObject or an object of any type which is valid to instantiate
     *                       a json.org.JSONObject
     * @param secret         the private key
     * @param requestPacket  an object which can be parsed into a JSONObject
     */
    public Init (Service service, Object securityPacket, String secret, Object requestPacket)
    {
 
        // First validate and set the arguments
        this.validateRequiredArgs(service, securityPacket, secret);
        
        this.validateRequestPacket(requestPacket);
        
        // Set any service specific options
        this.setServiceOptions();

        // Generate the signature based on the arguments provided
        this.securityPacket.put("signature", this.generateSignature());
    }
    
    /**
     * Setter method for action. If an action is required, it should be set before generate() is called
     * @param action the required action (e.g. get or post)
     */
    public void setAction(String action)
    {
    	this.action = action;
    	
    	// Re-generate the signature, as an action is now set
        this.securityPacket.put("signature", this.generateSignature());
    }
    
    /**
     * Generate the data necessary to make a request to one of the
     * Learnosity products/services.
     *
     * @return A JSON string
     */
    public String generate()
    {
        JSONObject output = new JSONObject();
        String outputString = "";

		switch (this.service) {
			case Assess:
			case Author:
			case Data:
			case Items:
			case Reports:

				// Add the security packet (with signature) to the output
				output.put("security", this.securityPacket);

				// Add the action if necessary (Data API)
				if (!this.action.isEmpty()) {
					output.put("action", this.action);
				}

				if (this.service == Data) {
					return output.getJSONObject("security").toString();
				} else if (this.service == Assess) {
					return this.requestString;
				}

				outputString = output.toString();
				// Add the request packet if available
				if (Strings.isNullOrEmpty(this.requestString)) {
					outputString = outputString.substring(0, outputString.length() - 1) + ",";
					outputString = outputString + "\"request\":" + this.requestString + "}";
				}
				break;
			case Questions:
				// Make a copy of security packet (with signature) to the root of output
				output = new JSONObject(this.securityPacket, JSONObject.getNames(this.securityPacket));

				// Remove the `domain` key from the security packet
				output.remove("domain");

				outputString = output.toString();
				// Merge the request packet if necessary. Note: to make sure we don't change the
				// order of key/value pairs in the json, we manipulate the json string instead of
				// the json object and then parsing into a string
				if (this.requestString != "") {
					outputString = outputString.substring(0, outputString.length() - 1) + ",";
					outputString = outputString + this.requestString.substring(1);
				}
			case Events:
				// Add the security packet (with signature) to the output
				output.put("security", this.securityPacket);
				outputString = output.toString();

				// Add the request packet as key 'config' if available
				if (this.requestString != "") {
					outputString = outputString.substring(0, outputString.length() - 1) + ",";
					outputString = outputString + "\"config\":" + this.requestString + "}";
				}
		}
        return outputString;
    }

    /**
     * Generate a signature hash for the request, this includes:
     *  - the security credentials
     *  - the `request` packet (a JSON string) if passed
     *  - the `action` value if passed
     *
     * @return A signature hash for the request authentication
     */
    public String generateSignature()
    {
        ArrayList<String> signatureArray = new ArrayList<String>();

        // Create a pre-hash string based on the security credentials
        // The order is important
        for (String key : this.VALID_SECURITY_KEYS) {
            if (this.securityPacket.has(key)) {
                signatureArray.add(this.securityPacket.getString(key));
            }
        }

        
        // Add the secret
        signatureArray.add(this.secret);
        // Add the requestPacket if necessary
        if (this.signRequestData && !this.requestString.isEmpty()) {
            signatureArray.add(this.requestString);
        }

        // Add the action if necessary
        if (!this.action.isEmpty()) {
            signatureArray.add(this.action);
        }

        return this.hashValue(signatureArray);
    }

    /**
     * Hash an array value
     *
     * @param  value the array to hash
     *
     * @return string The hashed string
     */
    private String hashValue(ArrayList<String> value)
    {
        String valueString = "";
        for (String entry : value) {
            if (valueString.equals("")) {
                valueString = entry;
            } else {
                valueString += "_" + entry;
            }
        }
        return DigestUtils.sha256Hex(valueString);
    }

    /**
     * Set any options for services that aren't generic
     */
    private void setServiceOptions()
    {
        if (this.service.equals("assess") ||
            this.service.equals("questions")) {

            this.signRequestData = false;
            // The Assess API holds data for the Questions API that includes
            // security information and a signature. Retrieve the security
            // information from $this and generate a signature for the
            // Questions API
            if (this.service.equals("assess") &&
                    this.requestPacket != null &&
                    this.requestPacket.has("questionsApiActivity")) {
                JSONObject questionsApi = this.requestPacket.getJSONObject("questionsApiActivity");
                String domain = "assess.learnosity.com";
                ArrayList<String> signatureArray = new ArrayList<String>();
                if (this.securityPacket.has("domain")) {
                    domain = this.securityPacket.getString("domain");
                } else if (questionsApi.has("domain")) {
                    domain = questionsApi.getString("domain");
                }

                for (String key : new String[] {"consumer_key" , "timestamp" , "user_id"}) {
                    questionsApi.put(key, this.securityPacket.getString(key));
                }
                signatureArray.add(this.securityPacket.getString("consumer_key"));
                signatureArray.add(domain);
                signatureArray.add(this.securityPacket.getString("timestamp"));
                signatureArray.add(this.securityPacket.getString("user_id"));
                signatureArray.add(this.secret);
                questionsApi.put("signature", this.hashValue(signatureArray));
            }
        } else if (this.service.equals("items")) {
            // The Items API requires a user_id, so we make sure it's a part
            // of the security packet as we share the signature in some cases
            if (!this.securityPacket.has("user_id") &&
                 this.requestPacket.has("user_id")) {
                this.securityPacket.put("user_id", this.requestPacket.getString("user_id"));
            }
        } else if (this.service.equals("events")) {
        	this.signRequestData = false;
        	JSONObject hashedUsers = new JSONObject();
        	if (this.requestPacket.has("users")) {
        		JSONArray users = this.requestPacket.getJSONArray("users");
        		for (int i = 0; i < users.length(); i++) {
        			String user = users.getString(i);
        			String stringToHash = user + this.secret;
        			String userHash = DigestUtils.sha256Hex(stringToHash);
        			hashedUsers.put(user, userHash);
        			}
        		this.requestPacket.put("users", hashedUsers);
        		this.requestString = this.requestPacket.toString();
        	}
        }
    }

    /**
     * Validate the required parameters of the constructor and set them if ok 
     *
     * @param  service
     * @param  securityPacket
     * @param  secret
     */
    private void validateRequiredArgs(Service service, Object securityPacket, String secret)
    {

		checkNotNull(service,"The `service` argument is required");
        checkArgument(!Strings.isNullOrEmpty(secret),"The `secret` argument must be a valid string");

        this.service = service;

        // In case the user gave us a securityPacket String, convert to a JSONOBject
        this.validateSecurityPacket(securityPacket);
        
        this.secret = secret;
    }

    /**
     * Validates the request packet argument
     * @param requestPacket
     */
    private void validateRequestPacket(Object requestPacket)
    {
        if (requestPacket instanceof JSONObject) {
            this.requestPacket = new JSONObject(requestPacket.toString());
            this.requestString = requestPacket.toString();
        } else {
            if (requestPacket instanceof String) {
                this.requestPacket = new JSONObject((String)requestPacket);
                this.requestString = (String)requestPacket;
            } else if (requestPacket instanceof Map) {
                this.requestPacket = new JSONObject((Map)requestPacket);
                this.requestString = this.requestPacket.toString();
            } else {
                // Try to make a JSONObject out of a hopefully valid java bean
                this.requestPacket = new JSONObject(requestPacket);
                this.requestString = this.requestPacket.toString();
            }
        }
        checkArgument(this.requestPacket.length() >= 0,
				"The requestPacket cannot be empty.");

    }

    /**
     * Validate the security packet argument
     * @param securityPacket
     */
    private void validateSecurityPacket (Object securityPacket)
    {
		checkNotNull(securityPacket);
        if (securityPacket instanceof JSONObject) {
            this.securityPacket = new JSONObject(securityPacket.toString());
        } else {
            if (securityPacket instanceof String) {
                this.securityPacket = new JSONObject((String)securityPacket);
            } else if (securityPacket instanceof Map) {
                this.securityPacket = new JSONObject((Map)securityPacket);
            } else {
                // Try to make a JSONObject out of a hopefully valid java bean
                this.securityPacket = new JSONObject(securityPacket);
            }
        }

		if (this.service == Questions) {
			checkArgument(this.securityPacket.has("user_id"),
					"If using the questions api, a user id needs to be specified");
		}

        checkArgument(this.securityPacket.length() > 0,
            "The security packet argument cannot be empty expected arguments: "+ Joiner.on(",").join(VALID_SECURITY_KEYS));

        
        Iterator<String> keyIter = this.securityPacket.keys();
        while (keyIter.hasNext()) {
            String key = keyIter.next();
            checkArgument(this.VALID_SECURITY_KEYS.contains(key),
                "Invalid key found in the security packet: " + key);

        }

        if (!this.securityPacket.has("timestamp")) {
            DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmm");
            dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            Date date = new Date();
            this.securityPacket.put("timestamp", dateFormat.format(date));
        }
    }   
}
