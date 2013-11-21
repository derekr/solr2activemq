package com.solr2activemq;

import com.solr2activemq.messaging.MessagingSystem;
import com.solr2activemq.pojos.ExceptionSolrQuery;
import com.solr2activemq.pojos.SolrQuery;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.lucene.queryParser.ParseException;
import org.apache.solr.core.SolrConfig;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.codehaus.jackson.map.ObjectMapper;

import javax.jms.JMSException;
import javax.jms.TextMessage;

/**
 * User: dbraga - Date: 11/19/13
 */
public class SolrToActiveMQHandler extends SearchHandler {

  private static String ACTIVEMQ_BROKER_URI;
  private static int ACTIVEMQ_BROKER_PORT;
  private static String ACTIVEMQ_DESTINATION_TYPE;
  private static String ACTIVEMQ_DESTINATION_NAME;

  private static String SOLR_HOSTNAME;
  private static int SOLR_PORT;
  private static String SOLR_POOLNAME;
  private static String SOLR_CORENAME;

  private TextMessage message;

  private static final String EXCEPTION = "exception";
  private static final String INFO = "info";

  private static MessagingSystem messagingSystem;

  private ObjectMapper mapper;

  private static boolean needsBootstrap = true;

  // TODO: move to messaging system?
  public void bootstrapMessagingSystem(){
    try{
      messagingSystem = new MessagingSystem(ACTIVEMQ_BROKER_URI, ACTIVEMQ_BROKER_PORT);
      messagingSystem.createConnection();
      messagingSystem.createSession();
      //TODO: handle topics, not just queues
      messagingSystem.createDestination(ACTIVEMQ_DESTINATION_NAME);
      messagingSystem.createProducer();
      log.info("SolrToActiveMQHandler: Bootstrapping messaging system done.");
      needsBootstrap = false;
    }
    catch (JMSException e){
      log.error("SolrToActiveMQHandler: Bootstrapping messaging system failed.\n" + e);
      needsBootstrap = true;
    }
  }

  // TODO: move to messaging system?
  public static TextMessage addMessageProperties(TextMessage message, String msgType) throws JMSException {
    message.setStringProperty("hostname", SOLR_HOSTNAME);
    message.setIntProperty("port", SOLR_PORT);
    message.setStringProperty("poolName", SOLR_POOLNAME);
    message.setStringProperty("coreName", SOLR_CORENAME);
    message.setStringProperty("msgType", msgType);
    return message;
  }

  @Override
  public void inform(SolrCore core) {
    super.inform(core);
    // Loading configuration
    SolrConfig config = core.getSolrConfig();

    /*  You can specify configuration in the solrconfig.xml e.g:

    <solrToActiveMQConfig activeMQBrokerUri="example.broker.com" activeMQBrokerPort="61616" activeMQBrokerDestinationType="queue" activeMQBrokerDestinationName="test_queue" solrHostname="exampleHostname" solrPort="8983" solrPoolname="examplePoolName" solrCorename="collection"/>

     */

    ACTIVEMQ_BROKER_URI = config.get("//solrToActiveMQConfig/@activeMQBrokerUri", "localhost");
    ACTIVEMQ_BROKER_PORT = config.getInt("//solrToActiveMQConfig/@activeMQBrokerPort", 61616);
    ACTIVEMQ_DESTINATION_TYPE = config.get("//solrToActiveMQConfig/@activeMQBrokerDestinationType", "queue");
    ACTIVEMQ_DESTINATION_NAME = config.get("//solrToActiveMQConfig/@activeMQBrokerDestinationName", "solrToActiveMQ_queue");
    SOLR_HOSTNAME = config.get("//solrToActiveMQConfig/@solrHostname", "localhost");
    SOLR_PORT = config.getInt("//solrToActiveMQConfig/@solrPort", 8983);
    SOLR_POOLNAME = config.get("//solrToActiveMQConfig/@solrPoolname", "default");
    SOLR_CORENAME = config.get("//solrToActiveMQConfig/@solrCorename", "collection");

    log.info("SolrToActiveMQHandler: loaded configuration:"  +
            "\n\tACTIVEMQ_BROKER_URI: " + ACTIVEMQ_BROKER_URI +
            "\n\tACTIVEMQ_BROKER_PORT: " + ACTIVEMQ_BROKER_PORT +
            "\n\tACTIVEMQ_DESTINATION_TYPE: " + ACTIVEMQ_DESTINATION_TYPE +
            "\n\tACTIVEMQ_DESTINATION_NAME: " + ACTIVEMQ_DESTINATION_NAME +
            "\n\tSOLR_HOSTNAME: " + SOLR_HOSTNAME +
            "\n\tSOLR_PORT: " + SOLR_PORT +
            "\n\tSOLR_POOLNAME: " + SOLR_POOLNAME +
            "\n\tSOLR_CORENAME: " + SOLR_CORENAME
    );

    //Bootstrapping messaging system
    bootstrapMessagingSystem();
    // Create the JSON object mapper
    mapper= new ObjectMapper();
  }

  public void handleException(Throwable t,SolrQueryRequest req, SolrQueryResponse rsp){
      try {
        message = addMessageProperties(messagingSystem.getSession().createTextMessage(mapper.writeValueAsString(new ExceptionSolrQuery(
                (rsp.getToLog().get("params") == null ) ? "" : (String)rsp.getToLog().get("params"),
                (rsp.getToLog().get("hits") == null )? 0 : (Integer)rsp.getToLog().get("hits"),
                rsp.getEndTime()-req.getStartTime(),
                (rsp.getToLog().get("path") == null ) ? "" : (String)rsp.getToLog().get("path"),
                (rsp.getToLog().get("webapp") == null ) ? "" :(String)rsp.getToLog().get("webapp"),
                ExceptionUtils.getStackTrace(t)
        ))), EXCEPTION);
        messagingSystem.sendMessage(message);
      } catch (Exception e) {
        e.printStackTrace();
      }
  }

  public void handleRequestBody(SolrQueryRequest req, org.apache.solr.response.SolrQueryResponse rsp) throws Exception, ParseException, InstantiationException, IllegalAccessException {
    try{
      super.handleRequestBody(req,rsp);
      if (!needsBootstrap){
        message = addMessageProperties(messagingSystem.getSession().createTextMessage(mapper.writeValueAsString(new SolrQuery(
                (rsp.getToLog().get("params") == null ) ? "" : (String)rsp.getToLog().get("params"),
                (rsp.getToLog().get("hits") == null )? 0 : (Integer)rsp.getToLog().get("hits"),
                rsp.getEndTime()-req.getStartTime(),
                (rsp.getToLog().get("path") == null ) ? "" : (String)rsp.getToLog().get("path"),
                (rsp.getToLog().get("webapp") == null ) ? "" :(String)rsp.getToLog().get("webapp")
        ))), INFO);
        messagingSystem.sendMessage(message);
      }
    } catch (Exception e){
        if (!needsBootstrap){
          handleException(e,req,rsp);
        }
        throw e;
    }

  }

}
