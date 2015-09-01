package org.chocosolver.parser.xcsp.tools;

import org.chocosolver.parser.xcsp.tools.XValues.*;
import org.chocosolver.parser.xcsp.tools.XVariables.TypeVar;
import org.chocosolver.parser.xcsp.tools.XVariables.Var;

import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.chocosolver.parser.xcsp.tools.XVariables.TypeVar.*;

/** In this class, we find intern classes for managing all types of domains. */
public class XDomains {

	/** The root interface to tag domain objects. */
	public static interface Dom {
	}

	/** A class for representing basic domains, i.e. domains for integer, symbolic, real and stochastic variables. */
	public static class DomBasic implements Dom {

		/** Returns the basic domain obtained by parsing the specified string, according to the value of the specified type. */
		public static DomBasic parse(String s, TypeVar type) {
			return type == integer ? new DomInteger(s) : type == symbolic ? new DomSymbolic(s) : type == real ? new DomReal(s) : DomStochastic.parse(s, type);
		}

		/** Returns the sequence of basic domains for the variables in the specified array. */
		public static DomBasic[] domainsFor(Var[] vars) {
			return Stream.of(vars).map(x -> ((DomBasic) x.dom)).toArray(DomBasic[]::new);
		}

		/**
		 * Returns the sequence of basic domains for the variables in the first row of the specified two-dimensional array, provided that variables of the other
		 * rows have similar domains. Returns null otherwise.
		 */
		public static DomBasic[] domainsFor(Var[][] varss) {
			DomBasic[] doms = domainsFor(varss[0]);
			for (Var[] vars : varss)
				if (IntStream.range(0, vars.length).anyMatch(i -> doms[i] != vars[i].dom))
					return null;
			return doms;
		}

		/**
		 * The values of the domain: for an integer domain, values are IntegerEntity, for a symbolic domain, values are String, and for a float domain, values
		 * are RealInterval.
		 */
		public final Object[] values;

		/** Builds a basic domain, with the specified values. */
		protected DomBasic(Object[] values) {
			this.values = values;
		}

		@Override
		public String toString() {
			return "Values: " + XUtility.join(values);
		}
	}

	/** The class for representing the domain of an integer variable. */
	public static class DomInteger extends DomBasic {

		/** Builds an integer domain, with the integer values (entities that are either integers or integer intervals) obtained by parsing the specified string. */
		protected DomInteger(String seq) {
			super(IntegerEntity.parseSeq(seq)); // must be already sorted.
		}

		/** Returns the first (smallest) value of the domain. It may be VAL_M_INFINITY for -infinity. */
		public long getFirstValue() {
			return ((IntegerEntity) values[0]).smallest();
		}

		/** Returns the last (greatest) value of the domain. It may be VAL_P_INFINITY for +infinity. */
		public long getLastValue() {
			return ((IntegerEntity) values[values.length - 1]).greatest();
		}

		/** Returns the smallest (the most efficient in term of space consumption) primitive that can be used for representing any value of the domain. */
		public TypePrimitive whichPrimitive() {
			return TypePrimitive.whichPrimitiveFor(getFirstValue(), getLastValue());
		}

		/** Returns true iff the domain contains the specified value. */
		public boolean contains(long v) {
			for (int left = 0, right = values.length - 1; left <= right;) {
				int center = (left + right) / 2;
				int res = ((IntegerEntity) values[center]).compareContains(v);
				if (res == 0)
					return true;
				if (res == -1)
					left = center + 1;
				else
					right = center - 1;
			}
			return false;
		}

		private Long nbValues; // cache for lazy initialization

		/** Returns the number of values in the domain, if the domain is finite. Return -1 otherwise. */
		public long getNbValues() {
			if (nbValues != null)
				return nbValues;
			if (getFirstValue() == XConstants.VAL_M_INFINITY || getLastValue() == XConstants.VAL_P_INFINITY)
				return nbValues = -1L; // infinite number of values
			long cnt = 0;
			for (IntegerEntity entity : (IntegerEntity[]) values)
				if (entity instanceof IntegerValue)
					cnt++;
				else {
					long diff = entity.width(), l = cnt + diff;
					XUtility.control(cnt == l - diff, "Overflow");
					cnt = l;
				}
			return nbValues = cnt;
		}
	}

	/** The class for representing the domain of a symbolic variable. */
	public static final class DomSymbolic extends DomBasic {

		/** Builds a symbolic domain, with the symbols obtained by parsing the specified string. */
		protected DomSymbolic(String seq) {
			super(XUtility.sort(seq.split("\\s+")));
		}

		/** Returns true iff the domain contains the specified value. */
		protected boolean contains(String s) {
			return Arrays.binarySearch((String[]) values, s) >= 0;
		}
	}

	/** The class for representing the domain of a real variable. */
	public static class DomReal extends DomBasic {

		/** Builds a real domain, with the intervals obtained by parsing the specified string. */
		protected DomReal(String seq) {
			super(RealInterval.parseSeq(seq));
		}
	}

	/** The class for representing the domain of a stochastic variable. */
	public static final class DomStochastic extends DomBasic {
		/** Returns the stochastic domain obtained by parsing the specified string, according to the specified type. */
		public static DomStochastic parse(String s, TypeVar type) {
			String[] toks = s.split("\\s+");
			Object[] values = new Object[toks.length];
			SimpleValue[] probas = new SimpleValue[toks.length];
			for (int i = 0; i < toks.length; i++) {
				String[] t = toks[i].split(":");
				values[i] = type == TypeVar.symbolic_stochastic ? t[0] : IntegerEntity.parse(t[0]);
				probas[i] = SimpleValue.parse(t[1]);
			}
			return new DomStochastic(values, probas);
		}

		/**
		 * The probabilities associated with the values of the domain: probas[i] is the probability of values[i]. Probabilities can be given as rational,
		 * decimal, or integer values (only, 0 and 1 for integer).
		 */
		public final SimpleValue[] probas;

		/** Builds a stochastic domain, with the specified values and the specified probabilities. */
		protected DomStochastic(Object[] values, SimpleValue[] probas) {
			super(values);
			this.probas = probas;
			assert values.length == probas.length;
		}

		@Override
		public String toString() {
			return super.toString() + " Probas: " + XUtility.join(probas);
		}
	}

	/** The interface to tag complex domains, i.e. domains for set or graph variables. */
	public static interface DomComplex extends Dom {
	}

	/** The class for representing the domain of a set variable. */
	public static final class DomSet implements DomComplex {
		/** Returns the set domain obtained by parsing the specified strings, according to the specified type. */
		public static DomSet parse(String req, String pos, TypeVar type) {
			return type == TypeVar.set ? new DomSet(IntegerEntity.parseSeq(req), IntegerEntity.parseSeq(pos))
					: new DomSet(req.split("\\s+"), pos.split("\\s+"));
		}

		/** The required and possible values. For an integer set domain, values are IntegerEntity. For a symbolic set domain, values are String. */
		public final Object[] required, possible;

		/** Builds a set domain, with the specified required and possible values. */
		protected DomSet(Object[] required, Object[] possible) {
			this.required = required;
			this.possible = possible;
		}

		@Override
		public String toString() {
			return "[{" + XUtility.join(required) + "},{" + XUtility.join(possible) + "}]";
		}
	}

	/** The class for representing the domain of a graph variable. */
	public static final class DomGraph implements DomComplex {
		/** Returns the graph domain obtained by parsing the specified strings, according to the specified type. */
		public static DomGraph parse(String reqV, String reqE, String posV, String posE, TypeVar type) {
			String[] rV = reqV.split("\\s+"), pV = posV.split("\\s+");
			String[][] rE = Stream.of(reqE.split(XConstants.DELIMITER_LISTS)).skip(1).map(tok -> tok.split("\\s*,\\s*")).toArray(String[][]::new);
			String[][] pE = Stream.of(posE.split(XConstants.DELIMITER_LISTS)).skip(1).map(tok -> tok.split("\\s*,\\s*")).toArray(String[][]::new);
			return new DomGraph(rV, pV, rE, pE);
		}

		/** The required and possible nodes (vertices). */
		public final String[] requiredV, possibleV;

		/** The required and possible edges or arcs. */
		public final String[][] requiredE, possibleE;

		/** Builds a graph domain, with the specified required and possible values (nodes and edges/arcs). */
		protected DomGraph(String[] requiredV, String[] possibleV, String[][] requiredE, String[][] possibleE) {
			this.requiredV = requiredV;
			this.possibleV = possibleV;
			this.requiredE = requiredE;
			this.possibleE = possibleE;
		}

		@Override
		public String toString() {
			return "[{" + XUtility.join(requiredV) + "-" + XUtility.join(requiredE) + "},{" + XUtility.join(possibleV) + "-" + XUtility.join(possibleE) + "}]";
		}
	}
}