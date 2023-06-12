package org.camunda.bpm.extension.commons.io.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.engine.delegate.DelegateTask;
import org.camunda.bpm.engine.delegate.TaskListener;
import org.camunda.bpm.extension.commons.io.ITaskEvent;
import org.camunda.bpm.extension.commons.io.socket.message.TaskEventMessage;
import org.camunda.bpm.extension.commons.io.socket.message.TaskMessage;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;

import org.springframework.data.redis.core.StringRedisTemplate;
//import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;
import java.util.logging.Logger;

import static org.camunda.bpm.extension.commons.utils.VariableConstants.FORM_URL;
import static org.camunda.bpm.extension.commons.utils.VariableConstants.APPLICATION_STATUS;
import static org.camunda.bpm.extension.commons.utils.VariableConstants.APPLICATION_ID;

/**
 * Camunda Event Listener.
 * This class intercepts all camunda task and push socket messages for web tier
 * updates.
 *
 * @author sumathi.thirumani@aot-technologies.com
 */
@Component
public class CamundaEventListener implements ITaskEvent {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // @Autowired
    // private SimpMessagingTemplate template;

    private final Logger LOGGER = Logger.getLogger(CamundaEventListener.class.getName());

    @Value("${websocket.messageType}")
    private String messageCategory;

    @Value("${websocket.messageEvents}")
    private String messageEvents;

    @Value("${websocket.enableRedis}")
    private boolean redisEnabled;

    @Resource(name = "bpmObjectMapper")
    private ObjectMapper bpmObjectMapper;

    @EventListener
    public void onTaskEventListener(DelegateTask taskDelegate) {
        LOGGER.info("Event triggered:" + taskDelegate.getId() + "-" + taskDelegate.getEventName() + "-"
                + taskDelegate.getProcessInstanceId());
        try {
            if (isRegisteredEvent(taskDelegate.getEventName())) {
                if (isAllowed(EventCategory.TASK_EVENT_DETAILS.name())) {
                    this.stringRedisTemplate.convertAndSend(getTopicNameForTaskDetail(),
                            bpmObjectMapper.writeValueAsString(getTaskMessage(taskDelegate)));
                }
                if (isAllowed(EventCategory.TASK_EVENT.name())) {
                    this.stringRedisTemplate.convertAndSend(getTopicNameForTask(),
                            bpmObjectMapper.writeValueAsString(getTaskEventMessage(taskDelegate)));
                }
                // convertAndSendMessage(getTopicNameForTaskDetail(),
                // getTaskMessage(taskDelegate));
                // if (isAllowed(EventCategory.TASK_EVENT.name())) {
                // convertAndSendMessage(getTopicNameForTask(),
                // getTaskEventMessage(taskDelegate));
                // }
            }
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    private void convertAndSendMessage(String topicName, Object message) throws JsonProcessingException {
        if (redisEnabled) {
            this.stringRedisTemplate.convertAndSend(topicName, bpmObjectMapper.writeValueAsString(message));
        } else {
            this.stringRedisTemplate.convertAndSend(topicName, bpmObjectMapper.writeValueAsString(message));
        }
    }

    private TaskMessage getTaskMessage(DelegateTask taskDelegate) {
        TaskMessage taskObj = new TaskMessage();
        BeanUtils.copyProperties(taskDelegate, taskObj);
        taskObj.setVariables(getVariables(taskDelegate));
        return taskObj;
    }

    private TaskEventMessage getTaskEventMessage(DelegateTask taskDelegate) {
        TaskEventMessage taskObj = new TaskEventMessage();
        BeanUtils.copyProperties(taskDelegate, taskObj);
        return taskObj;
    }

    private boolean isAllowed(String category) {
        return Arrays.asList(StringUtils.split(messageCategory, ",")).contains(category);
    }

    private boolean isRegisteredEvent(String eventName) {
        if ("ALL".equalsIgnoreCase(messageEvents)) {
            return true;
        }
        return getRegisteredEvents().contains(eventName);
    }

    private List<String> getRegisteredEvents() {
        if ("DEFAULT".equalsIgnoreCase(messageEvents)) {
            return getDefaultRegisteredEvents();
        }
        return Arrays.asList(StringUtils.split(messageEvents, ","));
    }

    private Map<String, Object> getVariables(DelegateTask taskDelegate) {
        List<String> configMap = getElements();
        Map<String, Object> variables = new HashMap<>();
        for (String entry : configMap) {
            if (taskDelegate.getVariables().containsKey(entry)) {
                variables.put(entry, taskDelegate.getVariable(entry));
            }
        }
        return variables;
    }

    private List<String> getElements() {
        return new ArrayList<>(Arrays.asList(APPLICATION_ID, FORM_URL, APPLICATION_STATUS));
    }

    private List<String> getDefaultRegisteredEvents() {
        return Arrays.asList(TaskListener.EVENTNAME_CREATE,
                TaskListener.EVENTNAME_UPDATE,
                TaskListener.EVENTNAME_COMPLETE);
    }

    /**
     * private ObjectMapper getObjectMapper() {
     * return new ObjectMapper();
     * }
     */

    enum EventCategory {
        TASK_EVENT, TASK_EVENT_DETAILS;
    }

}