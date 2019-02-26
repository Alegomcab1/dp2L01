
package controllers;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import security.UserAccountService;
import services.ConfigurationService;
import services.MemberService;
import domain.Configuration;
import domain.Member;
import forms.FormObjectMember;

@Controller
@RequestMapping("/anonymous")
public class AnonymousController extends AbstractController {

	@Autowired
	private MemberService			memberService;

	@Autowired
	private ConfigurationService	configurationService;
	@Autowired
	private UserAccountService		userAccountService;


	public AnonymousController() {
		super();
	}

	//------------------------ MEMBER -----------------------------------------------------	

	//Create
	@RequestMapping(value = "/createMember", method = RequestMethod.GET)
	public ModelAndView createCustomer() {
		ModelAndView result;

		FormObjectMember formObjectMember = new FormObjectMember();
		formObjectMember.setTermsAndConditions(false);

		result = this.createEditModelAndView(formObjectMember);

		return result;
	}

	//SAVE
	@RequestMapping(value = "/createMember", method = RequestMethod.POST, params = "save")
	public ModelAndView save(@Valid FormObjectMember formObjectMember, BindingResult binding) {

		ModelAndView result;

		Member member = new Member();
		member = this.memberService.createMember();

		Configuration configuration = this.configurationService.getConfiguration();
		String prefix = configuration.getSpainTelephoneCode();

		member = this.memberService.reconstruct(formObjectMember, binding);

		if (binding.hasErrors())
			result = this.createEditModelAndView(member);
		else
			try {
				//Confirmacion terminos y condiciones
				if (!formObjectMember.getTermsAndConditions())
					if (LocaleContextHolder.getLocale().getLanguage().toUpperCase().contains("ES")) {
						binding.addError(new FieldError("formObjectMember", "termsAndConditions", formObjectMember.getTermsAndConditions(), false, null, null, "Debe aceptar los terminos y condiciones"));
						return this.createEditModelAndView(member);
					} else {
						binding.addError(new FieldError("formObjectMember", "termsAndConditions", formObjectMember.getTermsAndConditions(), false, null, null, "You must accept the terms and conditions"));
						return this.createEditModelAndView(member);
					}

				//Confirmacion contraseņa
				if (!formObjectMember.getPassword().equals(formObjectMember.getConfirmPassword()))
					if (LocaleContextHolder.getLocale().getLanguage().toUpperCase().contains("ES")) {
						binding.addError(new FieldError("formObjectMember", "password", formObjectMember.getPassword(), false, null, null, "Las contraseņas no coinciden"));
						return this.createEditModelAndView(member);
					} else {
						binding.addError(new FieldError("formObjectMember", "password", formObjectMember.getPassword(), false, null, null, "Passwords don't match"));
						return this.createEditModelAndView(member);
					}

				if (member.getEmail().matches("[\\w.%-]+\\<[\\w.%-]+\\@+\\>|[\\w.%-]+")) {
					if (LocaleContextHolder.getLocale().getLanguage().toUpperCase().contains("ES")) {
						binding.addError(new FieldError("member", "email", member.getEmail(), false, null, null, "No sigue el patron ejemplo@dominio.asd o alias <ejemplo@dominio.asd>"));
						return this.createEditModelAndView(member);
					} else {
						binding.addError(new FieldError("member", "email", member.getEmail(), false, null, null, "Dont follow the pattern example@domain.asd or alias <example@domain.asd>"));
						return this.createEditModelAndView(member);
					}

				} else if (member.getPhoneNumber().matches("(\\+[0-9]{1,3})(\\([0-9]{1,3}\\))([0-9]{4,})$") || member.getPhoneNumber().matches("(\\+[0-9]{1,3})([0-9]{4,})$"))
					this.memberService.saveCreate(member);
				else if (member.getPhoneNumber().matches("([0-9]{4,})$")) {
					member.setPhoneNumber(prefix + member.getPhoneNumber());
					this.memberService.saveCreate(member);
				} else
					this.memberService.saveCreate(member);

				result = new ModelAndView("redirect:/security/login.do");

			} catch (Throwable oops) {
				result = this.createEditModelAndView(member, "brotherhood.commit.error");

			}
		return result;
	}
	//MODEL AND VIEW FORM OBJECT
	protected ModelAndView createEditModelAndView(FormObjectMember formObjectMember) {
		ModelAndView result;

		result = this.createEditModelAndView(formObjectMember, null);

		return result;
	}

	protected ModelAndView createEditModelAndView(FormObjectMember formObjectMember, String messageCode) {
		ModelAndView result;

		result = new ModelAndView("anonymous/createMember");
		result.addObject("formObjectMember", formObjectMember);

		result.addObject("message", messageCode);

		return result;
	}

	//MODEL AND VIEW MEMBER
	protected ModelAndView createEditModelAndView(Member member) {
		ModelAndView result;

		result = this.createEditModelAndView(member, null);

		return result;
	}

	protected ModelAndView createEditModelAndView(Member member, String messageCode) {
		ModelAndView result;

		result = new ModelAndView("anonymous/createMember");
		result.addObject("member", member);
		result.addObject("message", messageCode);

		return result;
	}

}
