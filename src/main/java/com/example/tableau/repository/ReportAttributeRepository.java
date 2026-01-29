package com.example.tableau.repository;

import com.example.tableau.entity.ReportAttribute;
import com.example.tableau.enums.StatusFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportAttributeRepository extends JpaRepository<ReportAttribute, Long> {

    Optional<ReportAttribute> findByAssetIdAndWorksheetIdAndSiteId(String assetId, String worksheetId, String siteId);

    Optional<ReportAttribute> findByAssetId(String assetId);

    List<ReportAttribute> findByAssetIdIn(List<String> assetIds);

    List<ReportAttribute> findByWorksheetId(String worksheetId);

    List<ReportAttribute> findBySiteId(String siteId);

    @Query("SELECT r FROM ReportAttribute r WHERE r.worksheet.id = :worksheetDbId")
    List<ReportAttribute> findByWorksheetDbId(@Param("worksheetDbId") Long worksheetDbId);

    List<ReportAttribute> findByStatusFlag(StatusFlag statusFlag);

    List<ReportAttribute> findByStatusFlagNot(StatusFlag statusFlag);

    List<ReportAttribute> findByIsCalculatedTrue();

    @Modifying
    @Query("UPDATE ReportAttribute r SET r.statusFlag = :statusFlag WHERE r.assetId = :assetId AND r.worksheetId = :worksheetId AND r.siteId = :siteId")
    int updateStatusFlagByAssetIdAndWorksheetIdAndSiteId(@Param("assetId") String assetId, @Param("worksheetId") String worksheetId, @Param("siteId") String siteId, @Param("statusFlag") StatusFlag statusFlag);

    @Modifying
    @Query("UPDATE ReportAttribute r SET r.statusFlag = :statusFlag WHERE r.worksheet.id = :worksheetDbId")
    int updateStatusFlagByWorksheetDbId(@Param("worksheetDbId") Long worksheetDbId, @Param("statusFlag") StatusFlag statusFlag);

    @Query("SELECT r FROM ReportAttribute r WHERE r.statusFlag != 'DELETED'")
    List<ReportAttribute> findAllActive();

    @Query("SELECT r FROM ReportAttribute r WHERE r.siteId = :siteId AND r.statusFlag != 'DELETED'")
    List<ReportAttribute> findAllActiveBySiteId(@Param("siteId") String siteId);

    boolean existsByAssetIdAndWorksheetIdAndSiteId(String assetId, String worksheetId, String siteId);
}
