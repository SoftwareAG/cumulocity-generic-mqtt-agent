package mqttagent.services;

import com.cumulocity.microservice.subscription.service.MicroserviceSubscriptionsService;
import mqttagent.callbacks.GenericCallback;
import mqttagent.configuration.MQTTConfiguration;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MQTTClient {

    private final Logger logger = LoggerFactory.getLogger(MQTTClient.class);

    @Autowired
    MQTTConfiguration mqttConfiguration;

    private MqttClient mqttClient;

    @Autowired
    private C8yAgent c8yAgent;

    @Autowired
    private MicroserviceSubscriptionsService subscriptionsService;

    @Autowired
    private GenericCallback genericCallback;

    public void init() throws MqttException {
        String prefix = mqttConfiguration.getUseTLS() ? "ssl://" : "tcp://";
        String broker = prefix + mqttConfiguration.getHost() + ":" + mqttConfiguration.getPort();
        logger.info("Connecting to MQTT Broker {}", broker);
        this.mqttClient = new MqttClient(broker, mqttConfiguration.getClientId());
    }

    public void reconnect() {
        this.disconnect();
        while (!isConnected()) {
            logger.info("Try to reestablish the MQTT connection in 5s ...");

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                logger.error("Error on reconnect: ", e);
            }
            try {
                connect();
                //Uncomment this if you want to subscribe on start on "#"
                subscribe("#", null);
                subscribe("$SYS/#", null);
            } catch (MqttException e) {
                logger.error("Error on reconnect: ", e);
            }
        }
    }

    public void connect() throws MqttException {
        MqttConnectOptions connOpts = new MqttConnectOptions();
        connOpts.setCleanSession(true);
        connOpts.setAutomaticReconnect(true);
        connOpts.setUserName(mqttConfiguration.getUser());
        connOpts.setPassword(mqttConfiguration.getPassword().toCharArray());
        this.mqttClient.connect(connOpts);
        logger.info("Successfully connected to Broker {}", mqttClient.getServerURI());
        subscriptionsService.runForTenant(c8yAgent.tenant, () -> {
            c8yAgent.createEvent("Successfully connected to Broker " + mqttClient.getServerURI(), "mqtt_status_event", DateTime.now(), null);
        });
    }

    public boolean isConnected() {
        return mqttClient != null ? mqttClient.isConnected() : false;
    }

    public void disconnect() {
        logger.error("Disconnecting from MQTT Broker "+ mqttClient.getServerURI());
        try {
            if (this.mqttClient != null && this.mqttClient.isConnected()) {
                this.mqttClient.unsubscribe("#");
                this.mqttClient.unsubscribe("$SYS");
                this.mqttClient.disconnect();
            }
            logger.error("Disconnected from MQTT Broker "+ mqttClient.getServerURI());
        } catch (MqttException e) {
            logger.error("Error on disconnecting MQTT Client: ", e);
        }
    }

    public void subscribe(String topic, Integer qos) throws MqttException {
        if (mqttClient != null) {
            logger.info("Subscribing on topic {}", topic);
            subscriptionsService.runForTenant(c8yAgent.tenant, () -> {
                c8yAgent.createEvent("Subscribing on topic " + topic, "mqtt_status_event", DateTime.now(), null);
            });
            mqttClient.setCallback(genericCallback);
            if (this.mqttClient.isConnected())
                if (qos != null)
                    mqttClient.subscribe(topic, qos);
                else
                    mqttClient.subscribe(topic);
            else {
                connect();
                if (qos != null)
                    mqttClient.subscribe(topic, qos);
                else
                    mqttClient.subscribe(topic);
            }
            logger.info("Successfully subscribed on topic {}", topic);
        }
    }

    public void unsubscribe(String topic) throws MqttException {
        if (mqttClient != null) {
            logger.info("Unsubscribing on topic {}", topic);
            subscriptionsService.runForTenant(c8yAgent.tenant, () -> {
                c8yAgent.createEvent("Unsubscribing on topic " + topic, "mqtt_status_event", DateTime.now(), null);
            });
            mqttClient.unsubscribe(topic);
            logger.info("Successfully unsubscribed on topic {}", topic);
        }
    }
}
