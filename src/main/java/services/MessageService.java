
package services;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Validator;

import domain.Actor;
import domain.Admin;
import domain.Box;
import domain.Brotherhood;
import domain.Member;
import domain.Message;
import repositories.MessageRepository;
import security.LoginService;
import security.UserAccount;

@Service
@Transactional
public class MessageService {

	@Autowired
	private MessageRepository messageRepository;

	@Autowired
	private ActorService actorService;

	@Autowired
	private BoxService boxService;

	@Autowired
	private ConfigurationService configurationService;

	@Autowired
	private MemberService memberService;

	@Autowired
	private AdminService adminService;

	@Autowired
	private BrotherhoodService brotherhoodService;

	@Autowired
	private Validator validator;

	// Actualizar caja que tiene el mensaje EN ESTE ORDEN
	// ACTUALIZAR CAJA SIN EL MENSAJE
	// BORRAR EL MENSAJE Y TODAS SUS COPIAS
	public void delete(Message m) {
		this.messageRepository.delete(m);
	}

	public Message sendMessageBroadcasted(Message message) {

		this.actorService.loggedAsActor();

		Box boxNotification = new Box();

		Box boxSent = new Box();

		Message messageSaved = this.messageRepository.saveAndFlush(message);
		Message messageCopy = this.create(messageSaved.getSubject(), messageSaved.getBody(), messageSaved.getPriority(),
				messageSaved.getReceiver());
		messageCopy.setTags(messageSaved.getTags());

		Message messageCopySaved = this.messageRepository.save(messageCopy);

		boxSent = this.boxService.getSentBoxByActor(messageSaved.getSender());
		boxNotification = this.boxService.getNotificationBoxByActor(messageSaved.getReceiver());

		// Guardar la box con ese mensaje;

		boxNotification.getMessages().add(messageCopySaved);
		boxSent.getMessages().add(messageSaved);
		// boxRecieved.setMessages(list);
		this.boxService.saveSystem(boxSent);
		this.boxService.saveSystem(boxNotification);
		this.actorService.save(messageSaved.getSender());
		this.actorService.flushSave(messageSaved.getReceiver());

		return messageSaved;
	}

	// Metodo para enviar un mensaje a un ACTOR (O varios, que tambien puede ser)
	public Message sendMessage(Message message) {

		this.actorService.loggedAsActor();

		Actor actorRecieved = message.getReceiver();
		Actor senderActor = message.getSender();

		Box boxRecieved = new Box();
		Box boxSpam = new Box();
		Box boxSent = new Box();

		List<String> spam = new ArrayList<String>();

		spam = this.configurationService.getSpamWords();

		Message messageSaved = this.messageRepository.save(message);
		Message messageCopy = this.create(messageSaved.getSubject(), messageSaved.getBody(), messageSaved.getPriority(),
				messageSaved.getReceiver());
		Message messageCopySaved = this.messageRepository.save(messageCopy);

		boxSent = this.boxService.getSentBoxByActor(messageSaved.getSender());
		boxRecieved = this.boxService.getRecievedBoxByActor(actorRecieved);
		boxSpam = this.boxService.getSpamBoxByActor(actorRecieved);

		// Guardar la box con ese mensaje;

		if (this.configurationService.isStringSpam(messageSaved.getBody(), spam)
				|| this.configurationService.isStringSpam(messageSaved.getSubject(), spam)) {
			boxSent.getMessages().add(messageSaved);
			boxSpam.getMessages().add(messageCopySaved);

			this.boxService.saveSystem(boxSent);
			this.boxService.saveSystem(boxSpam);
			this.actorService.save(messageSaved.getSender());
			this.actorService.save(actorRecieved);

		} else {
			boxRecieved.getMessages().add(messageCopySaved);
			boxSent.getMessages().add(messageSaved);
			// boxRecieved.setMessages(list);
			this.boxService.saveSystem(boxSent);
			this.boxService.saveSystem(boxRecieved);
			this.actorService.save(messageSaved.getSender());
			this.actorService.save(actorRecieved);
		}

		// Calculamos la Polarity y el hasSpam
		this.actorService.updateActorSpam(senderActor);
		this.configurationService.computeScore(senderActor);
		return messageSaved;
	}

	public void sendNotificationDropOut(Brotherhood bro) {

		Member loggedMember = this.memberService.loggedMember();
		Admin admin = this.adminService.getSystem();
		Box sentAdmin = this.boxService.getSentBoxByActor(admin);
		Box notMem = this.boxService.getNotificationBoxByActor(loggedMember);
		Box notBro = this.boxService.getNotificationBoxByActor(bro);

		Message messageBro = new Message();
		Message messageMem = new Message();

		messageBro = this.createNotification("Drop out notification / Notificacion de salida",
				"The user " + loggedMember.getUserAccount().getUsername()
						+ " has dropped out the brotherhood. / El usuario "
						+ loggedMember.getUserAccount().getUsername() + " ha dejado la hermandad.",
				"HIGH", "DROP OUT", bro);
		messageMem = this.createNotification(
				"Drop out notification / Notificacion de salida", "You have dropped out the brotherhood "
						+ bro.getTitle() + ". / Has dejado la hermandad " + bro.getTitle() + ".",
				"HIGH", "DROP OUT", loggedMember);

		Message copyBro = new Message();
		Message copyMem = new Message();
		copyBro = this.createNotification(messageBro.getSubject(), messageBro.getBody(), messageBro.getPriority(),
				messageBro.getTags(), messageBro.getSender());
		copyMem = this.createNotification(messageMem.getSubject(), messageMem.getBody(), messageMem.getPriority(),
				messageMem.getTags(), messageMem.getSender());
		copyBro.setReceiver(bro);
		copyMem.setReceiver(loggedMember);
		Message saveBro = this.messageRepository.save(copyBro);
		Message saveMem = this.messageRepository.save(copyMem);

		List<Message> messAdmin = sentAdmin.getMessages();
		List<Message> messMem = notMem.getMessages();
		List<Message> messBro = notBro.getMessages();
		messAdmin.add(saveBro);
		messAdmin.add(saveMem);
		messMem.add(saveMem);
		messBro.add(saveBro);
		sentAdmin.setMessages(messAdmin);
		notMem.setMessages(messMem);
		notBro.setMessages(messBro);
		this.boxService.flushSave(sentAdmin);
		this.boxService.flushSave(notMem);
		this.boxService.flushSave(notBro);

		this.actorService.save(messageBro.getSender());
		this.actorService.save(messageMem.getSender());
		this.actorService.save(messageBro.getReceiver());
		this.actorService.save(messageMem.getReceiver());
	}

	public void sendNotificationBroEnrolMem(Member mem) {

		Brotherhood loggedBrotherhood = this.brotherhoodService.loggedBrotherhood();
		Admin admin = this.adminService.getSystem();
		Box sentAdmin = this.boxService.getSentBoxByActor(admin);
		Box notMem = this.boxService.getNotificationBoxByActor(mem);
		Box notBro = this.boxService.getNotificationBoxByActor(loggedBrotherhood);
		Message messageBro = null;
		Message messageMem = null;

		messageBro = this.createNotification("Enrol notification / Notificacion de inscripcion",
				"You have accepted the user " + mem.getUserAccount().getUsername()
						+ " to the brotherhood. / Has aceptado al usuario " + mem.getUserAccount().getUsername()
						+ " a la hermandad.",
				"HIGH", "ENROLMENT", loggedBrotherhood);
		messageMem = this.createNotification("Enrol notification / Notificacion de inscripcion",
				"You have been accepted into the brotherhood " + loggedBrotherhood.getTitle()
						+ ". / Has sido aceptado en la hermandad " + loggedBrotherhood.getTitle() + ".",
				"HIGH", "ENROLMENT", mem);

		this.messageRepository.save(messageBro);
		this.messageRepository.save(messageMem);
		Message copyBro = new Message();
		Message copyMem = new Message();
		copyBro = this.createNotification(messageBro.getSubject(), messageBro.getBody(), messageBro.getPriority(),
				messageBro.getTags(), messageBro.getSender());
		copyMem = this.createNotification(messageMem.getSubject(), messageMem.getBody(), messageMem.getPriority(),
				messageMem.getTags(), messageMem.getSender());
		copyBro.setReceiver(loggedBrotherhood);
		copyMem.setReceiver(mem);
		Message saveBro = this.messageRepository.save(copyBro);
		Message saveMem = this.messageRepository.save(copyMem);

		List<Message> messAdmin = sentAdmin.getMessages();
		List<Message> messMem = notMem.getMessages();
		List<Message> messBro = notBro.getMessages();
		messAdmin.add(saveBro);
		messAdmin.add(saveMem);
		messMem.add(saveMem);
		messBro.add(saveBro);
		sentAdmin.setMessages(messAdmin);
		notMem.setMessages(messMem);
		notBro.setMessages(messBro);
		this.boxService.flushSave(sentAdmin);
		this.boxService.flushSave(notMem);
		this.boxService.flushSave(notBro);

		this.actorService.save(messageBro.getSender());
		this.actorService.save(messageMem.getSender());
		this.actorService.save(messageBro.getReceiver());
		this.actorService.save(messageMem.getReceiver());

	}

	public Message sendMessageAnotherSender(Message message) {

		Actor actorRecieved = message.getReceiver();
		List<String> spam = new ArrayList<String>();

		spam = this.configurationService.getSpamWords();

		Box boxRecieved = new Box();
		Box boxSpam = new Box();
		Box boxNotification = new Box();

		Message messageSaved = this.messageRepository.save(message);
		Message messageCopy = this.createNotification(messageSaved.getSubject(), messageSaved.getBody(),
				messageSaved.getPriority(), message.getTags(), messageSaved.getReceiver());
		Message messageCopySaved = this.messageRepository.save(messageCopy);
		boxRecieved = this.boxService.getRecievedBoxByActor(actorRecieved);
		boxSpam = this.boxService.getSpamBoxByActor(actorRecieved);
		boxNotification = this.boxService.getNotificationBoxByActor(actorRecieved);

		// Guardar la box con ese mensaje;

		if (this.configurationService.isStringSpam(messageSaved.getBody(), spam)
				|| this.configurationService.isStringSpam(messageSaved.getSubject(), spam)) {
			boxNotification.getMessages().add(messageSaved);
			boxSpam.getMessages().add(messageCopySaved);

			this.boxService.saveSystem(boxNotification);
			this.boxService.saveSystem(boxSpam);
			this.actorService.save(messageSaved.getSender());
			this.actorService.save(actorRecieved);

		} else {
			boxRecieved.getMessages().add(messageCopySaved);
			boxNotification.getMessages().add(messageSaved);
			// boxRecieved.setMessages(list);
			this.boxService.saveSystem(boxNotification);
			this.boxService.saveSystem(boxRecieved);
			this.actorService.save(messageSaved.getSender());
			this.actorService.save(actorRecieved);
		}
		return messageSaved;
	}

	public Message save(Message message) {
		return this.messageRepository.save(message);

	}

	public Message create() {

		this.actorService.loggedAsActor();

		UserAccount userAccount;
		userAccount = LoginService.getPrincipal();

		Date thisMoment = new Date();
		thisMoment.setTime(thisMoment.getTime() - 1000);

		Message message = new Message();
		Actor sender = this.actorService.getActorByUsername(userAccount.getUsername());
		Actor receiver = new Actor();
		message.setMoment(thisMoment);
		message.setSubject("");
		message.setBody("");
		message.setPriority("");
		message.setReceiver(receiver);
		message.setTags("");
		message.setSender(sender);

		return message;
	}

	public Message createSecurityBreach() {

		this.actorService.loggedAsActor();

		UserAccount userAccount;
		userAccount = LoginService.getPrincipal();

		Date thisMoment = new Date();
		thisMoment.setTime(thisMoment.getTime() - 1000);

		Message message = new Message();
		Actor sender = this.actorService.getActorByUsername(userAccount.getUsername());
		Actor receiver = new Actor();
		message.setMoment(thisMoment);
		message.setSubject("Error de seguridad / Security Breach");
		message.setBody("Esto es un mensaje para informar que ha habido una brecha de seguridad // This is a message to inform about a security breach");
		message.setPriority("");
		message.setReceiver(receiver);
		message.setTags("Security, Breach, Notification, Urgent, Important");
		message.setSender(sender);

		return message;
	}

	public Message create(String Subject, String body, String priority, Actor recipient) {

		this.actorService.loggedAsActor();

		UserAccount userAccount;
		userAccount = LoginService.getPrincipal();

		Date thisMoment = new Date();
		thisMoment.setTime(thisMoment.getTime() - 1);

		Message message = new Message();

		Actor sender = this.actorService.getActorByUsername(userAccount.getUsername());

		message.setMoment(thisMoment);
		message.setSubject(Subject);
		message.setBody(body);
		message.setPriority(priority);
		message.setReceiver(recipient);
		message.setTags("");
		message.setSender(sender);

		return message;
	}

	public Message createNotification(String Subject, String body, String priority, String tags, Actor recipient) {
		this.actorService.loggedAsActor();

		Date thisMoment = new Date();
		thisMoment.setTime(thisMoment.getTime() - 1);

		Message message = new Message();

		Actor sender = this.actorService.getActorByUsername("system");

		message.setMoment(thisMoment);
		message.setSubject(Subject);
		message.setBody(body);
		message.setPriority(priority);
		message.setReceiver(recipient);
		message.setTags(tags);
		message.setSender(sender);

		return message;
	}

	public void updateMessage(Message message, Box box) { // Posible problema
		// con copia

		this.actorService.loggedAsActor();
		UserAccount userAccount;
		userAccount = LoginService.getPrincipal();
		Actor actor = this.actorService.getActorByUsername(userAccount.getUsername());

		for (Box b : actor.getBoxes()) {
			if (b.getMessages().contains(message))
				b.getMessages().remove(message);
			// list.remove(message);
			// b.setMessages(list);
			if (b.getName().equals(box.getName())) {
				List<Message> list = b.getMessages();
				list.add(message);
				b.setMessages(list);
			}
		}
	}

	public void deleteMessageToTrashBox(Message message) {
		UserAccount userAccount;
		userAccount = LoginService.getPrincipal();
		Actor actor = this.actorService.getActorByUsername(userAccount.getUsername());

		// Box currentBox = this.boxService.getCurrentBoxByMessage(message);

		List<Box> currentBoxes = new ArrayList<>();

		for (Box b : actor.getBoxes())
			if (b.getMessages().contains(message))
				currentBoxes.add(b);

		Box trash = this.boxService.getTrashBoxByActor(actor);

		// When an actor removes a message from a box other than trash box, it
		// is moved to the trash box;
		for (Box currentBox : currentBoxes)
			if (currentBox.equals(trash)) {
				for (Box b : actor.getBoxes())
					if (b.getMessages().contains(message)) {
						b.getMessages().remove(message);
						this.messageRepository.delete(message);
					}
			} else
				this.updateMessage(message, trash);
		// this.messageRepository.save(message); Si se pone en el metodo
		// updateMessage no hace falta aqui
	}

	public void copyMessage(Message message, Box box) {

		this.actorService.loggedAsActor();
		UserAccount userAccount;
		userAccount = LoginService.getPrincipal();
		Actor actor = this.actorService.getActorByUsername(userAccount.getUsername());

		for (Box b : actor.getBoxes())
			if (b.getName().equals(box.getName())) {
				List<Message> list = b.getMessages();
				list.add(message);
				b.setMessages(list);
			}
	}

	public List<Message> findAll() {
		return this.messageRepository.findAll();
	}

	public List<Message> findAll2() {
		return this.messageRepository.findAll2();
	}

	public Message findOne(int id) {
		return this.messageRepository.findOne(id);
	}

	public List<Message> getMessagesByBox(Box b) {
		return b.getMessages();
	}

	public domain.Message reconstruct(domain.Message messageTest, BindingResult binding) {

		UserAccount userAccount;
		userAccount = LoginService.getPrincipal();
		Actor actor = this.actorService.getActorByUsername(userAccount.getUsername());

		domain.Message result;
		if (messageTest.getId() == 0) {
			result = messageTest;
			result.setSender(actor);
			Date thisMoment = new Date();
			thisMoment.setTime(thisMoment.getTime() - 1000);
			result.setMoment(thisMoment);

		} else {
			result = this.messageRepository.findOne(messageTest.getId());

			result.setBody(messageTest.getBody());
			result.setPriority(messageTest.getPriority());
			result.setTags(messageTest.getTags());
			result.setSubject(messageTest.getSubject());
			result.setReceiver(messageTest.getReceiver());
			// result.setMoment(messageTest.getMoment());
		}

		this.validator.validate(result, binding);
		return result;

	}

	public Message reconstructDelete(Message messageTest) {

		UserAccount userAccount;
		userAccount = LoginService.getPrincipal();
		Actor actor = this.actorService.getActorByUsername(userAccount.getUsername());

		Message result;

		result = this.messageRepository.findOne(messageTest.getId());

		return result;

	}

}
