/*
 * Copyright (c) 2014 European Spallation Source
 * Copyright (c) 2014 Cosylab d.d.
 *
 * This file is part of Controls Configuration Database.
 *
 * Controls Configuration Database is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the License,
 * or any newer version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see https://www.gnu.org/licenses/gpl-2.0.txt
 */
package org.openepics.discs.conf.dl;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.openepics.discs.conf.dl.annotations.SlotsLoader;
import org.openepics.discs.conf.dl.common.AbstractDataLoader;
import org.openepics.discs.conf.dl.common.DataLoader;
import org.openepics.discs.conf.dl.common.DataLoaderResult;
import org.openepics.discs.conf.dl.common.ExcelImportFileReader;
import org.openepics.discs.conf.util.TestUtility;

/**
 * Helper class for {@link SlotsDataLoaderIT} and {@link SlotsAndSlotPairsDataLoaderIT}
 *
 * @author <a href="mailto:andraz.pozar@cosylab.com">Andraž Požar</a>
 *
 */
public class SlotsDataLoaderHelper {

    @Inject @SlotsLoader private DataLoader slotsDataLoader;

    final static int NUM_OF_SLOTS_IF_FAILURE = 1;
    final static int NUM_OF_SLOTS_IF_SUCCESS = 156;
    final static int NUM_OF_SLOT_PAIRS_IF_FAILURE = 0;
    final static int NUM_OF_SLOT_PAIRS_IF_SUCCESS = 155;

    public DataLoaderResult importSlots(final String slotsImportFileName) throws IOException {
        List<Pair<Integer, List<String>>> slotsFileInputRows = null;

        if (slotsImportFileName != null) {
            final InputStream slotDataStream = this.getClass().getResourceAsStream(TestUtility.DATALOADERS_PATH + slotsImportFileName);
            slotsFileInputRows = ExcelImportFileReader.importExcelFile(slotDataStream,
                    AbstractDataLoader.DEFAULT_EXCEL_TAMPLATE_DATA_START_ROW, slotsDataLoader.getDataWidth());
            slotDataStream.close();
        } else {
            slotsFileInputRows = null;
        }

        return slotsDataLoader.loadDataToDatabase(slotsFileInputRows, null);
    }
}