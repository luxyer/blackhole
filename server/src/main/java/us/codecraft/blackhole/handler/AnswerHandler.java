package us.codecraft.blackhole.handler;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Message;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;
import org.xbill.DNS.Type;

import us.codecraft.blackhole.container.RecordBuilder;
import us.codecraft.blackhole.container.ServerContext;
import us.codecraft.blackhole.zones.AnswerContainer;
import us.codecraft.blackhole.zones.AnswerProvider;
import us.codecraft.blackhole.zones.PatternContainer;
import us.codecraft.blackhole.zones.SafeBoxAnswerProvider;

/**
 * @author yihua.huang@dianping.com
 * @date Dec 14, 2012
 */
@Component
public class AnswerHandler implements Handler, InitializingBean {

	private List<AnswerProvider> answerProviders;

	private Logger logger = Logger.getLogger(getClass());

	@Autowired
	private AnswerContainer answerContainer;

	@Autowired
	private PatternContainer patternContainer;

	@Autowired
	private SafeBoxAnswerProvider safeBoxAnswerProvider;

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
	 */
	@Override
	public void afterPropertiesSet() throws Exception {
		answerProviders = new LinkedList<AnswerProvider>();
		answerProviders.add(answerContainer);
		answerProviders.add(patternContainer);
		answerProviders.add(safeBoxAnswerProvider);
	}

	// b._dns-sd._udp.0.129.37.10.in-addr.arpa.
	private final Pattern filterPTRPattern = Pattern
			.compile(".*\\.(\\d+\\.\\d+\\.\\d+\\.\\d+\\.in-addr\\.arpa\\.)");

	private String filterPTRQuery(String query) {
		Matcher matcher = filterPTRPattern.matcher(query);
		if (matcher.matches()) {
			return matcher.group(1);
		} else {
			return query;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see us.codecraft.blackhole.server.Handler#handle(org.xbill.DNS.Message,
	 * org.xbill.DNS.Message)
	 */
	@Override
	public boolean handle(Message request, Message response) {
		Record question = request.getQuestion();
		String query = question.getName().toString();
		int type = question.getType();
		if (type == Type.PTR) {
			query = filterPTRQuery(query);
		}
		// some client will query with any
		if (type == Type.ANY) {
			type = Type.A;
		}
		if (logger.isDebugEnabled()) {
			logger.debug("query \t" + Type.string(type) + "\t"
					+ DClass.string(question.getDClass()) + "\t" + query);
		}
		for (AnswerProvider answerProvider : answerProviders) {
			String answer = answerProvider.getAnswer(query, type);
			if (answer != null) {
				try {
					Record record = new RecordBuilder()
							.dclass(question.getDClass())
							.name(question.getName()).answer(answer).type(type)
							.toRecord();
					response.addRecord(record, Section.ANSWER);
					if (logger.isDebugEnabled()) {
						logger.debug("answer\t" + Type.string(type) + "\t"
								+ DClass.string(question.getDClass()) + "\t"
								+ answer);
					}
					ServerContext.setHasRecord(true);
					return true;
				} catch (Throwable e) {
					logger.warn("handling exception " + e);
				}
			}
		}
		return false;
	}

}
