
package services;

import java.util.ArrayList;
import java.util.List;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Validator;

import repositories.FloatRepository;
import domain.Brotherhood;
import domain.Float;
import domain.Procession;
import forms.FormObjectProcessionFloat;

@Service
@Transactional
public class FloatService {

	@Autowired
	private FloatRepository		floatRepository;

	@Autowired
	private BrotherhoodService	brotherhoodService;
	@Autowired
	private ProcessionService	processionService;
	@Autowired
	private Validator			validator;


	public Float reconstruct(Float floatt, BindingResult binding) {
		Float result = new Float();

		if (floatt.getId() == 0) {
			result = floatt;
			this.validator.validate(result, binding);
		} else {
			result = this.floatRepository.findOne(floatt.getId());
			result.setTitle(floatt.getTitle());
			result.setDescription(floatt.getDescription());

			result.setPictures(floatt.getPictures());

			this.validator.validate(result, binding);
		}
		return result;
	}
	public List<Float> showAssignedFloats(Procession procession) {
		List<Float> floatts = new ArrayList<Float>();
		floatts = procession.getFloats();
		return floatts;
	}

	public List<Float> showAllFloats() {
		List<Float> floatts = new ArrayList<Float>();
		floatts = this.floatRepository.findAll();
		return floatts;
	}

	public List<Float> showBrotherhoodFloats() {
		Brotherhood bro = new Brotherhood();
		bro = this.brotherhoodService.loggedBrotherhood();
		List<Float> floatts = new ArrayList<Float>();
		floatts = bro.getFloats();
		return floatts;
	}

	public List<Float> findAll() {
		return this.floatRepository.findAll();
	}

	public Float findOne(final int id) {
		return this.floatRepository.findOne(id);
	}

	public void remove(Float floatt) {
		//No se pueden eliminar pasos asignados a procesiones en final mode

		this.brotherhoodService.loggedAsBrotherhood();
		Brotherhood bro = new Brotherhood();
		bro = this.brotherhoodService.loggedBrotherhood();
		List<Procession> pro = new ArrayList<Procession>();

		pro = this.brotherhoodService.getProcessionsByBrotherhood(bro);
		Assert.isTrue(this.allProcesionsDraftMode(pro));
		for (final Procession p : pro)
			if (p.getFloats().contains(floatt))
				p.getFloats().remove(floatt);
		bro.getFloats().remove(floatt);
		this.floatRepository.delete(floatt);
	}

	public Float save(Float floatt) {

		//Obtener float list
		//quitar float antiguo y a�adir el nuevo
		//Hacer set del float list modificado
		//Save procession

		//Obtener loggedBrotherhood

		//A PARTIR DE AQUI PUEDE QUE SEA OPCIONAL
		//Quitar procession antigua y a�adir nueva
		//Obt

		this.brotherhoodService.loggedAsBrotherhood();
		Brotherhood loggedBrotherhood = new Brotherhood();
		Float floattSaved = new Float();
		loggedBrotherhood = this.brotherhoodService.loggedBrotherhood();

		Assert.isTrue(!(loggedBrotherhood.getArea().equals(null)));

		floattSaved = this.floatRepository.save(floatt);

		loggedBrotherhood.getFloats().remove(floatt);
		loggedBrotherhood.getFloats().add(floattSaved);
		this.brotherhoodService.save(loggedBrotherhood);

		return floattSaved;
	}
	public Float create() {
		final Float floatt = new Float();
		final List<String> pictures = new ArrayList<String>();

		floatt.setPictures(pictures);
		floatt.setTitle("");
		floatt.setDescription("");

		return floatt;
	}

	public Boolean allProcesionsDraftMode(final List<Procession> pro) {
		final Boolean res = true;
		for (final Procession p : pro)
			if (p.getIsDraftMode() == false)
				return true;
		return res;
	}

	public void AssingFloatToProcession(final Float floatt, final Procession procession) {
		Assert.isTrue(procession.getIsDraftMode() == true);
		if (!(procession.getFloats().contains(floatt)))
			procession.getFloats().add(floatt);
		this.processionService.save(procession);
	}

	public void UnAssingFloatToProcession(Float floatt, Procession procession) {
		Assert.isTrue(procession.getIsDraftMode() == true);
		if (procession.getFloats().contains(floatt))
			procession.getFloats().remove(floatt);
		this.processionService.save(procession);
	}

	public Float reconstructForm(FormObjectProcessionFloat formObjectProcessionFloat, BindingResult binding) {
		domain.Float result = new domain.Float();

		result.setTitle(formObjectProcessionFloat.getTitle());
		result.setDescription(formObjectProcessionFloat.getDescription());

		//		this.validator.validate(result, binding);

		return result;
	}

}