package com.intuit.karate;

import com.intuit.karate.validator.Validator;
import java.util.Map;
import java.util.logging.Level;
import javax.net.ssl.SSLContext;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.glassfish.jersey.logging.LoggingFeature;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class ScriptContext {
    
    private static final Logger logger = LoggerFactory.getLogger(ScriptContext.class);
    
    private static final String KARATE_NAME = "karate";
    
    protected final ScriptValueMap vars;
    protected final Client client;
    protected final Map<String, Validator> validators;
    protected final String featureDir;
    protected final ClassLoader fileClassLoader;
    protected final String env; 

    // needed for 3rd party code
    public ScriptValueMap getVars() {
        return vars;
    }        

    public ScriptContext(boolean test, String featureDir, ClassLoader fileClassLoader, String env) {
        this.featureDir = featureDir;
        this.fileClassLoader = fileClassLoader;
        this.env = env;
        validators = Script.getDefaultValidators();
        vars = new ScriptValueMap();
        Script.assign(ScriptValueMap.VAR_READ, FileUtils.getFileReaderFunction(), this);
        Script.assign(ScriptValueMap.VAR_HEADERS, "function(){}", this);
        if (test) {
            logger.trace("karate init in test mode, http client disabled");
            client = null;
            return;
        }
        ClientBuilder clientBuilder = ClientBuilder.newBuilder()
                .register(MultiPartFeature.class);
        if (logger.isDebugEnabled()) {
            clientBuilder.register(new LoggingFeature(
                    java.util.logging.Logger.getLogger(LoggingFeature.DEFAULT_LOGGER_NAME),
                    Level.SEVERE,
                    LoggingFeature.Verbosity.PAYLOAD_TEXT, null));
        }          
        SSLContext sslContext =  SslUtils.getSslContext();
        clientBuilder.sslContext(sslContext);
        clientBuilder.hostnameVerifier((host, session) -> true);
        clientBuilder.register(new RequestFilter(this));
        client = clientBuilder.build();                             
        // auto config
        try {
            Script.callAndUpdateVars("read('classpath:karate-config.js')", null, this);
        } catch (Exception e) {
            logger.warn("start-up configuration failed, missing or bad 'karate-config.js' - {}", e.getMessage());
        }
        logger.trace("karate context init - initial properties: {}", vars);
    }
    
    public void injectInto(ScriptObjectMirror som) {
        som.setMember(KARATE_NAME, new ScriptBridge(this));
        // convenience for users, can use 'karate' instead of 'this.karate'
        som.eval(String.format("var %s = this.%s", KARATE_NAME, KARATE_NAME));
        Map<String, Object> simple = Script.simplify(vars);
        for (Map.Entry<String, Object> entry : simple.entrySet()) {
            som.put(entry.getKey(), entry.getValue()); // update eval context
        }          
    }
    
}
