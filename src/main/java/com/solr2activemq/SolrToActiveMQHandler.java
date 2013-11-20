package com.solr2activemq;

import com.solr2activemq.messaging.MessagingSystem;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.lucene.queryParser.ParseException;
import org.apache.solr.core.SolrConfig;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.SolrQueryRequest;

import javax.jms.JMSException;

/**
 * User: dbraga - Date: 11/19/13
 */
public class SolrToActiveMQHandler extends SearchHandler {

  private static String ACTIVEMQ_BROKER_URI;
  private static int ACTIVEMQ_BROKER_PORT;
  private static String ACTIVEMQ_DESTINATION_TYPE;
  private static String ACTIVEMQ_DESTINATION_NAME;

  private static MessagingSystem messagingSystem;


  public void bootstrapMessagingSystem(){
    try{
      messagingSystem = new MessagingSystem(ACTIVEMQ_BROKER_URI,ACTIVEMQ_BROKER_PORT);
      messagingSystem.createConnection();
      messagingSystem.createSession();
      messagingSystem.createDestination(ACTIVEMQ_DESTINATION_NAME);
      messagingSystem.createProducer();
      log.info("Bootstrapping messaging system done.");
    }
    catch (JMSException e){
      log.error("Bootstrapping messaging system failed.\n" + e);
    }
  }

  @Override
  public void inform(SolrCore core) {
    /*  Configuration in the solrconfig.xml

    <solrToActiveMQConfig host="databroker02.sv2.trulia.com" port="61616" type="queue" destination="test_queue"/>
    <requestHandler name="/select" class="com.solr2activemq.SolrToActiveMQHandler"> ...
    */

    super.inform(core);
    // loading preferences
    SolrConfig config = core.getSolrConfig();

    ACTIVEMQ_BROKER_URI = config.get("//solrToActiveMQConfig/@host", "localhost");
    log.info("SolrToActiveMQHandler: ACTIVEMQ_BROKER_URI is set to be " + ACTIVEMQ_BROKER_URI);

    ACTIVEMQ_BROKER_PORT = config.getInt("//solrToActiveMQConfig/@port", 61616);
    log.info("SolrToActiveMQHandler: ACTIVEMQ_BROKER_PORT is set to be " + ACTIVEMQ_BROKER_PORT);

    ACTIVEMQ_DESTINATION_TYPE = config.get("//solrToActiveMQConfig/@type", "queue");
    log.info("SolrToActiveMQHandler: ACTIVEMQ_DESTINATION_TYPE is set to be " + ACTIVEMQ_DESTINATION_TYPE);

    ACTIVEMQ_DESTINATION_NAME = config.get("//solrToActiveMQConfig/@destination", "solrToActiveMQ_queue");
    log.info("SolrToActiveMQHandler: ACTIVEMQ_DESTINATION_NAME is set to be " + ACTIVEMQ_DESTINATION_NAME);
  }

  public void handleException(Throwable t){
   System.out.println("Exception class: " + t.getClass());
   System.out.println("Stack trace: " + ExceptionUtils.getStackTrace(t));
  }

  public void handleRequestBody(SolrQueryRequest req, org.apache.solr.response.SolrQueryResponse rsp) throws Exception, ParseException, InstantiationException, IllegalAccessException {
    System.out.println("Before handle: Exception: " + rsp.getException());
    try{
      super.handleRequestBody(req, rsp);
    } catch (ParseException pe){
      handleException(pe);
    } catch (InstantiationException ie){
      handleException(ie);
    } catch (IllegalAccessException ille){
      handleException(ille);
    } catch (Exception e){
      handleException(e);
    }



  }

}
