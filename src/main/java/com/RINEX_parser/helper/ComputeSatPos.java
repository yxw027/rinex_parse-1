package com.RINEX_parser.helper;

import java.util.Arrays;
import java.util.TimeZone;
import java.util.stream.IntStream;

import org.ejml.simple.SimpleMatrix;

import com.RINEX_parser.models.NavigationMsg;
import com.RINEX_parser.models.SBAS.LongTermCorr;

public class ComputeSatPos {
	public static Object[] computeSatPos(NavigationMsg Sat, double tSV, double tRX, LongTermCorr ltc, double ISC) {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		double Mu = 3.986005E14;// WGS-84 value of the Earth's universal gravitational parameter in GPS-IS

		long NumberSecondsWeek = 604800;
		double OMEGA_E_DOT = 7.2921151467E-5;// WGS-84 value of the Earth's rotation rate
		double F = -4.442807633E-10;

		double A = Math.pow(Sat.getSqrt_A(), 2);
		double n0 = Math.sqrt((Mu / (A * A * A)));

		double TOC = Sat.getTOC();

		double[] dX = new double[] { 0, 0, 0, 0 };
		double[] dXrate = new double[] { 0, 0, 0, 0 };
		double ToA = 0;
		// useSBAS corrections
		if (ltc != null) {
			dX = ltc.getDeltaX();
			ToA = ltc.getToA();
			if (ltc.getVelCode() == 1) {
				dXrate = ltc.getDeltaXrate();
			}

		}

		double SV_clock_bias = Sat.getSV_clock_bias() + dX[3] + (dXrate[3] * (tSV - ToA)) + ISC;
		double n = n0 + Sat.getDelta_n();
		double coeff1 = (SV_clock_bias + ((tSV - TOC) * Sat.getSV_clock_drift()) - Sat.getTGD())
				/ (1 + Sat.getSV_clock_drift());
		double coeff2 = ((n * F * Sat.getSqrt_A()) / (1 + Sat.getSV_clock_drift())) - 1;
		double delta_Ek = 0;
		double assumed_Ek = 0;
		double f_Ek = Sat.getM0() + (n * (tSV - Sat.getTOE() - coeff1));
		double assumed_f_Ek = assumed_Ek + (Sat.getE() * Math.sin(assumed_Ek) * coeff2);

		int count = 0;
		while (count < 10) {
			delta_Ek = (f_Ek - assumed_f_Ek) / (1 + (Sat.getE() * Math.cos(assumed_Ek)) * coeff2);
			assumed_Ek = assumed_Ek + delta_Ek;
			assumed_f_Ek = assumed_Ek + (Sat.getE() * Math.sin(assumed_Ek) * coeff2);

			count++;
		}
		double Ek = assumed_Ek;
		double Mk = Ek - (Sat.getE() * Math.sin(Ek));
		double tk = (Mk - Sat.getM0()) / n;
		double t = tk + Sat.getTOE();
		double relativistic_error = F * Sat.getE() * Sat.getSqrt_A() * Math.sin(Ek);
		double SatClockOffset = SV_clock_bias + ((t - TOC) * Sat.getSV_clock_drift()) - Sat.getTGD()
				+ relativistic_error;
		double Vk; // True anomaly

		double num = ((Math.sqrt(1 - Math.pow(Sat.getE(), 2))) * Math.sin(Ek));
		double denom = (Math.cos(Ek) - Sat.getE());

		Vk = Math.atan(num / denom);

		if (denom < 0) {
			if (num > 0) {
				Vk = Math.PI + Vk;
			} else {
				Vk = -Math.PI + Vk;
			}
		}

		double argument_of_latitude = Vk + Sat.getOmega();

		double argument_of_latitude_correction = (Sat.getCus() * Math.sin(2 * argument_of_latitude))
				+ (Sat.getCuc() * Math.cos(2 * argument_of_latitude));
		double radius_correction = (Sat.getCrc() * Math.cos(2 * argument_of_latitude))
				+ (Sat.getCrs() * Math.sin(2 * argument_of_latitude));
		double correction_to_inclination = (Sat.getCic() * Math.cos(2 * argument_of_latitude))
				+ (Sat.getCis() * Math.sin(2 * argument_of_latitude));

		double uk = argument_of_latitude + argument_of_latitude_correction; // corrected_argument_of_latitude
		double rk = (A * (1 - (Sat.getE() * Math.cos(Ek)))) + radius_correction; // corrected_radius
		double ik = Sat.getI0() + correction_to_inclination + (Sat.getIDOT() * tk);// corrected_inclination

		double xk_orbital = rk * Math.cos(uk);
		double yk_orbital = rk * Math.sin(uk);

		double ascNode = Sat.getOMEGA0() + ((Sat.getOMEGA_DOT() - OMEGA_E_DOT) * tk) - (OMEGA_E_DOT * Sat.getTOE());// Corrected

		double xk_ECEF = (xk_orbital * Math.cos(ascNode)) - (yk_orbital * Math.cos(ik) * Math.sin(ascNode)) + dX[0]
				+ dXrate[0] * (tSV - ToA);
		double yk_ECEF = (xk_orbital * Math.sin(ascNode)) + (yk_orbital * Math.cos(ik) * Math.cos(ascNode)) + dX[1]
				+ dXrate[1] * (tSV - ToA);
		double zk_ECEF = yk_orbital * Math.sin(ik) + dX[2] + dXrate[2] * (tSV - ToA);

		double eciArg = OMEGA_E_DOT * (tRX - t);
		double x_ECI = (xk_ECEF * Math.cos(eciArg)) + (yk_ECEF * Math.sin(eciArg));
		double y_ECI = -(xk_ECEF * Math.sin(eciArg)) + (yk_ECEF * Math.cos(eciArg));
		double z_ECI = zk_ECEF;

		// Deriving SV_clock_drift
		// Source - The study of GPS Time Transfer based on extended Kalman filter -
		// https://ieeexplore.ieee.org/document/6702097
		double Ek_dot = n / (1 - (Sat.getE() * Math.cos(Ek)));
		double relativistic_error_dot = F * Sat.getE() * Sat.getSqrt_A() * Math.cos(Ek) * Ek_dot;
		double SV_clock_drift_derived = Sat.getSV_clock_drift() + (2 * Sat.getSV_clock_drift_rate() * (t - TOC))
				+ relativistic_error_dot;

		// Deriving Satellite Velocity Vector
		// Source -
		// https://www.researchgate.net/publication/228995031_GPS_Satellite_Velocity_and_Acceleration_Determination_using_the_Broadcast_Ephemeris

		// RotMatrix is defined as the rotation matrix from the ICDorb to the ECEF.
		// orbital coordinate system used in the ICD-GPS-200c (ICDorb) is different from
		// the ‘‘natural ’’orbital system.

		double[][] _RotMartix = {
				{ Math.cos(ascNode), -Math.sin(ascNode) * Math.cos(ik), Math.sin(ascNode) * Math.sin(ik) },
				{ Math.sin(ascNode), Math.cos(ascNode) * Math.cos(ik), -Math.cos(ascNode) * Math.sin(ik) },
				{ 0, Math.sin(ik), Math.cos(ik) } };
		double ascNode_dot = Sat.getOMEGA_DOT() - OMEGA_E_DOT;
		double[][] _RotMatrixDot = {
				{ -Math.sin(ascNode) * ascNode_dot, -Math.cos(ascNode) * Math.cos(ik) * ascNode_dot,
						Math.cos(ascNode) * Math.sin(ik) * ascNode_dot },
				{ Math.cos(ascNode) * ascNode_dot, -Math.sin(ascNode) * Math.cos(ik) * ascNode_dot,
						Math.sin(ascNode) * Math.sin(ik) * ascNode_dot },
				{ 0, 0, 0 } };
		double[][] _rICDorb = { { rk * Math.cos(uk) }, { rk * Math.sin(uk) }, { 0 } };
		double Vk_dot = (A * A * Math.sqrt(1 - (Sat.getE() * Sat.getE())) * n) / (rk * rk);
		double radius_corr_dot = -2 * ((Sat.getCrc() * Math.sin(2 * argument_of_latitude))
				- (Sat.getCrs() * Math.cos(2 * argument_of_latitude))) * Vk_dot;
		double rk_dot = (A * Sat.getE() * Math.sin(Ek) * Ek_dot) + radius_corr_dot;
		double argument_of_latitude_correction_dot = -2 * ((Sat.getCuc() * Math.sin(2 * argument_of_latitude))
				- (Sat.getCus() * Math.cos(2 * argument_of_latitude))) * Vk_dot;
		double uk_dot = Vk_dot + argument_of_latitude_correction_dot;
		double[][] _rICDorbDot = { { (rk_dot * Math.cos(uk)) - (rk * Math.sin(uk) * uk_dot) },
				{ (rk_dot * Math.sin(uk)) + (rk * Math.cos(uk) * uk_dot) }, { 0 } };

		SimpleMatrix RotMatrix = new SimpleMatrix(_RotMartix);
		SimpleMatrix RotMatrixDot = new SimpleMatrix(_RotMatrixDot);
		SimpleMatrix rICDorb = new SimpleMatrix(_rICDorb);
		SimpleMatrix rICDorbDot = new SimpleMatrix(_rICDorbDot);

		SimpleMatrix _SV_velocity = (RotMatrixDot.mult(rICDorb)).plus(RotMatrix.mult(rICDorbDot));
		double[] SV_velocity = IntStream.range(0, 3).mapToDouble(i -> _SV_velocity.get(i, 0)).toArray();
		double modVel = Arrays.stream(SV_velocity).map(i -> i * i).reduce(0.0, (i, j) -> i + j);
		modVel = Math.sqrt(modVel);
		double[] ECEF_SatClkOff = new double[] { xk_ECEF, yk_ECEF, zk_ECEF, SatClockOffset };
		double[] ECI = new double[] { x_ECI, y_ECI, z_ECI };
		return new Object[] { ECEF_SatClkOff, SV_velocity, SV_clock_drift_derived, t, ECI };

	}

}
