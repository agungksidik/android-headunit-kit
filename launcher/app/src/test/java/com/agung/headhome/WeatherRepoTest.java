package com.agung.headhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WeatherRepoTest {

    @Test
    public void stripsAdministrativePrefix() {
        assertEquals("Sukamaju", WeatherRepo.shortPlaceName("Kecamatan Sukamaju"));
        assertEquals("Bogor", WeatherRepo.shortPlaceName("Kabupaten Bogor"));
        assertEquals("Bogor", WeatherRepo.shortPlaceName("kab. Bogor"));
        assertEquals("Pusat", WeatherRepo.shortPlaceName("Kota Administrasi Pusat"));
        assertEquals("Bandung", WeatherRepo.shortPlaceName("Kota Bandung"));
    }

    @Test
    public void leavesPlainNamesAlone() {
        assertEquals("Bandung", WeatherRepo.shortPlaceName("Bandung"));
        assertEquals("Desawisata", WeatherRepo.shortPlaceName("Desawisata"));
        assertEquals("", WeatherRepo.shortPlaceName(null));
    }
}
